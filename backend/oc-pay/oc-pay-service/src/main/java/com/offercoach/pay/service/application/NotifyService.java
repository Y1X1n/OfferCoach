package com.offercoach.pay.service.application;

import com.offercoach.pay.service.application.port.PaymentOrderRepository;
import com.offercoach.pay.service.channel.ChannelRegistry;
import com.offercoach.pay.service.channel.PaymentChannel;
import com.offercoach.pay.service.domain.ChannelCode;
import com.offercoach.pay.service.domain.OrderStatus;
import com.offercoach.pay.service.domain.PaymentOrder;
import com.offercoach.pay.service.error.PayException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 渠道回调处理：验签、归一化、幂等置为已支付并触发额度发放。
 *
 * <p>无论事件类型如何都向渠道返回成功应答，避免渠道重复推送；未识别或非支付成功事件只记录不处理。</p>
 */
@Service
public class NotifyService {

    private static final Logger log = LoggerFactory.getLogger(NotifyService.class);

    private final ChannelRegistry registry;
    private final PaymentOrderRepository orderRepository;
    private final EntitlementNotifier notifier;

    public NotifyService(
            ChannelRegistry registry, PaymentOrderRepository orderRepository, EntitlementNotifier notifier) {
        this.registry = registry;
        this.orderRepository = orderRepository;
        this.notifier = notifier;
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
            orderRepository.recordNotify(null, code.code(), null, null, false, false, exception.getMessage());
            throw exception;
        }
        boolean handled = applyPaid(notify);
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

    private boolean applyPaid(PaymentChannel.ChannelNotify notify) {
        if (!notify.paid() || notify.orderNo() == null || notify.orderNo().isBlank()) {
            return false;
        }
        boolean transitioned = orderRepository.markPaid(notify.orderNo(), notify.providerTxnId(), notify.paidAt());
        if (transitioned) {
            orderRepository.findByOrderNo(notify.orderNo()).ifPresent(notifier::notify);
            return true;
        }
        PaymentOrder order = orderRepository.findByOrderNo(notify.orderNo()).orElse(null);
        if (order != null && order.status() != OrderStatus.PAID) {
            log.warn("支付回调命中非待支付订单，需人工核对：orderNo={} status={}", order.orderNo(), order.status());
        }
        return false;
    }
}
