package com.offercoach.pay.service.application;

import com.offercoach.pay.service.application.port.PaymentOrderRepository;
import com.offercoach.pay.service.channel.ChannelRegistry;
import com.offercoach.pay.service.channel.PaymentChannel;
import com.offercoach.pay.service.domain.ChannelCode;
import com.offercoach.pay.service.domain.OrderStatus;
import com.offercoach.pay.service.domain.PaymentExceptionKind;
import com.offercoach.pay.service.domain.PaymentOrder;
import com.offercoach.pay.service.error.PayErrorKind;
import com.offercoach.pay.service.error.PayException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 渠道回调处理：验签、交叉校验、幂等置为已支付并触发额度发放。
 *
 * <p>验签只证明报文来自渠道，不代表报文与本地订单一致；因此还要校验订单存在、渠道一致、金额一致与流水号非空。
 * 校验不通过时不改订单状态、写入 {@code payment_exception} 并向渠道返回失败，让渠道重试并留下可追查的痕迹。
 * 无法再收敛的场景（如订单已被本地关闭后才收到支付成功）仍向渠道应答成功以免无意义重试，
 * 但必须落异常表并由扫描任务告警，不允许只写一行 WARN。</p>
 */
@Service
public class NotifyService {

    private static final Logger log = LoggerFactory.getLogger(NotifyService.class);
    private static final int MAX_MESSAGE = 200;

    private final ChannelRegistry registry;
    private final PaymentOrderRepository orderRepository;
    private final EntitlementNotifier notifier;
    private final PaymentExceptionRecorder exceptions;

    public NotifyService(
            ChannelRegistry registry,
            PaymentOrderRepository orderRepository,
            EntitlementNotifier notifier,
            PaymentExceptionRecorder exceptions) {
        this.registry = registry;
        this.orderRepository = orderRepository;
        this.notifier = notifier;
        this.exceptions = exceptions;
    }

    /**
     * 处理回调并返回渠道要求的应答体。
     */
    public String handle(ChannelCode code, Map<String, String> headers, String body) {
        PaymentChannel channel = registry.require(code);
        PaymentChannel.ChannelNotify notify;
        try {
            notify = channel.parseNotify(headers, body);
        } catch (PayException exception) {
            orderRepository.recordNotify(null, code.code(), null, null, false, false, truncate(exception.getMessage()));
            throw exception;
        }
        boolean handled;
        try {
            handled = applyPaid(code, notify);
        } catch (PayException exception) {
            orderRepository.recordNotify(
                    notify.orderNo(),
                    code.code(),
                    notify.eventType(),
                    notify.providerTxnId(),
                    true,
                    false,
                    truncate(exception.getMessage()));
            throw exception;
        }
        orderRepository.recordNotify(
                notify.orderNo(),
                code.code(),
                notify.eventType(),
                notify.providerTxnId(),
                true,
                handled,
                notify.tradeState());
        return channel.successResponseBody();
    }

    private boolean applyPaid(ChannelCode code, PaymentChannel.ChannelNotify notify) {
        if (!notify.paid()) {
            return false;
        }
        String orderNo = notify.orderNo();
        if (orderNo == null || orderNo.isBlank()) {
            throw new PayException(PayErrorKind.VALIDATION_FAILED, "支付回调缺少订单号");
        }
        PaymentOrder order = orderRepository.findByOrderNo(orderNo).orElse(null);
        if (order == null) {
            exceptions.record(
                    orderNo,
                    null,
                    PaymentExceptionKind.UNKNOWN_ORDER,
                    "回调订单在本地不存在",
                    notify.amountCents(),
                    code,
                    notify.providerTxnId());
            throw new PayException(PayErrorKind.VALIDATION_FAILED, "支付回调订单不存在");
        }
        if (order.channel() != code) {
            exceptions.record(
                    orderNo,
                    order.userId(),
                    PaymentExceptionKind.CHANNEL_MISMATCH,
                    "回调渠道=" + code.code() + " 订单渠道=" + order.channel().code(),
                    notify.amountCents(),
                    code,
                    notify.providerTxnId());
            throw new PayException(PayErrorKind.VALIDATION_FAILED, "支付回调渠道与订单不一致");
        }
        if (notify.amountCents() == null || notify.amountCents() != order.amountCents()) {
            exceptions.record(
                    orderNo,
                    order.userId(),
                    PaymentExceptionKind.AMOUNT_MISMATCH,
                    "回调金额=" + notify.amountCents() + " 订单金额=" + order.amountCents(),
                    notify.amountCents(),
                    code,
                    notify.providerTxnId());
            throw new PayException(PayErrorKind.VALIDATION_FAILED, "支付回调金额与订单不一致");
        }
        if (notify.providerTxnId() == null || notify.providerTxnId().isBlank()) {
            throw new PayException(PayErrorKind.VALIDATION_FAILED, "支付回调缺少渠道流水号");
        }
        boolean transitioned = orderRepository.markPaid(orderNo, notify.providerTxnId(), notify.paidAt());
        if (transitioned) {
            orderRepository.findByOrderNo(orderNo).ifPresent(notifier::notify);
            return true;
        }
        PaymentOrder current = orderRepository.findByOrderNo(orderNo).orElse(order);
        if (current.status() == OrderStatus.PAID) {
            log.info("重复支付回调，幂等忽略：orderNo={}", orderNo);
            return false;
        }
        // 订单已被本地关闭/失败后才收到支付成功：渠道侧资金已发生，重试不会改变本地状态，
        // 因此应答成功止住重推，同时入队等人工退款或补发。
        exceptions.record(
                orderNo,
                current.userId(),
                PaymentExceptionKind.PAID_ON_INACTIVE_ORDER,
                "收到支付成功回调时本地状态=" + current.status(),
                notify.amountCents(),
                code,
                notify.providerTxnId());
        log.error("已支付回调命中非待支付订单，需人工核对或退款：orderNo={} status={} channel={}", orderNo, current.status(), code.code());
        return false;
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= MAX_MESSAGE ? value : value.substring(0, MAX_MESSAGE);
    }
}
