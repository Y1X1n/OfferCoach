package com.offercoach.pay.service.application;

import com.offercoach.pay.api.PayDtos;
import com.offercoach.pay.service.application.port.PaymentOrderRepository;
import com.offercoach.pay.service.application.port.PaymentRefundRepository;
import com.offercoach.pay.service.channel.ChannelRegistry;
import com.offercoach.pay.service.channel.PaymentChannel;
import com.offercoach.pay.service.config.PayOpsProperties;
import com.offercoach.pay.service.domain.ChannelCode;
import com.offercoach.pay.service.domain.OrderStatus;
import com.offercoach.pay.service.domain.PaymentOrder;
import com.offercoach.pay.service.domain.PaymentRefund;
import com.offercoach.pay.service.domain.RefundStatus;
import com.offercoach.pay.service.error.PayErrorKind;
import com.offercoach.pay.service.error.PayException;
import com.offercoach.pay.service.support.OrderNumbers;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * 订单用例：创建、查询、关闭、退款与本地模拟确认。
 *
 * <p>渠道调用（下单 / 关单 / 退款）不放在事务内；幂等键保证重复创建与重复退款安全。</p>
 */
@Service
public class OrderService {

    private static final String NOTIFY_PENDING = "PENDING";

    private final PaymentOrderRepository orderRepository;
    private final PaymentRefundRepository refundRepository;
    private final ChannelRegistry registry;
    private final PaymentReservationService reservationService;
    private final EntitlementNotifier notifier;
    private final PayOpsProperties ops;

    public OrderService(
            PaymentOrderRepository orderRepository,
            PaymentRefundRepository refundRepository,
            ChannelRegistry registry,
            PaymentReservationService reservationService,
            EntitlementNotifier notifier,
            PayOpsProperties ops) {
        this.orderRepository = orderRepository;
        this.refundRepository = refundRepository;
        this.registry = registry;
        this.reservationService = reservationService;
        this.notifier = notifier;
        this.ops = ops;
    }

    /**
     * 创建订单并完成渠道下单；同一幂等键重复调用返回首次订单。
     */
    public PayDtos.OrderView createOrder(PayDtos.CreateOrderRequest request) {
        validateCreate(request);
        ChannelCode channelCode = ChannelCode.fromCode(request.channel());
        if (channelCode == null) {
            throw new PayException(PayErrorKind.VALIDATION_FAILED, "不支持的支付渠道");
        }
        PaymentOrder existing = orderRepository
                .findByUserIdempotency(
                        request.userId(), request.idempotencyKey().strip())
                .orElse(null);
        if (existing != null) {
            return OrderViews.of(existing);
        }
        PaymentChannel channel = registry.require(channelCode);
        if (!channel.available()) {
            throw new PayException(PayErrorKind.NOT_CONFIGURED, "支付渠道未启用：" + channelCode.code());
        }
        Instant now = Instant.now();
        PaymentOrder order = new PaymentOrder(
                0L,
                OrderNumbers.orderNo(),
                request.userId(),
                request.planCode().strip(),
                subject(request),
                request.amountCents(),
                currency(request),
                channelCode,
                OrderStatus.CREATED,
                null,
                null,
                null,
                request.idempotencyKey().strip(),
                NOTIFY_PENDING,
                0,
                now.plus(ops.getOrderExpireMinutes(), ChronoUnit.MINUTES),
                null,
                null,
                0,
                now,
                now);
        try {
            orderRepository.insert(order);
        } catch (DuplicateKeyException exception) {
            PaymentOrder concurrent = orderRepository
                    .findByUserIdempotency(request.userId(), order.idempotencyKey())
                    .orElseThrow(() -> new PayException(PayErrorKind.STATE_CONFLICT, "下单冲突，请重试"));
            return OrderViews.of(concurrent);
        }
        try {
            PaymentChannel.Prepay prepay = channel.prepay(order, request.clientIp());
            orderRepository.attachPrepay(order.orderNo(), prepay.codeUrl(), prepay.prepayId());
        } catch (PayException exception) {
            orderRepository.markFailed(order.orderNo());
            throw exception;
        } catch (RuntimeException exception) {
            orderRepository.markFailed(order.orderNo());
            throw new PayException(PayErrorKind.UPSTREAM_FAILED, "渠道下单失败", exception);
        }
        return OrderViews.of(orderRepository.findByOrderNo(order.orderNo()).orElse(order));
    }

    /**
     * 查询订单；非本人订单按不存在处理，纯读不改状态（过期关闭与支付对账由扫描任务完成）。
     */
    public PayDtos.OrderView getOrder(long userId, String orderNo) {
        return OrderViews.of(loadOwned(userId, orderNo));
    }

    /**
     * 关闭待支付订单。
     */
    public PayDtos.OrderView closeOrder(long userId, String orderNo) {
        PaymentOrder order = loadOwned(userId, orderNo);
        if (order.status() == OrderStatus.CREATED) {
            PaymentChannel channel = registry.find(order.channel());
            if (channel != null) {
                channel.close(order);
            }
            orderRepository.markClosed(orderNo);
            order = orderRepository.findByOrderNo(orderNo).orElse(order);
        }
        return OrderViews.of(order);
    }

    /**
     * 退款；预占在事务内完成，渠道调用在事务外。
     *
     * <p>已预占但未完成的退款（如进程中断）在重试时以同一 {@code out_refund_no} 幂等重发；
     * 渠道失败归还预占金额。</p>
     */
    public PayDtos.RefundView refund(long userId, String orderNo, PayDtos.RefundRequest request) {
        PaymentReservationService.Reservation reservation = reservationService.reserveRefund(userId, orderNo, request);
        PaymentRefund refund = reservation.refund();
        if (refund.status() == RefundStatus.CREATED) {
            PaymentChannel channel = registry.require(reservation.order().channel());
            PaymentChannel.RefundOutcome outcome = channel.refund(reservation.order(), refund);
            if (outcome.success()) {
                refundRepository.updateResult(refund.refundNo(), RefundStatus.SUCCESS, outcome.channelRefundId());
                orderRepository.finalizeRefund(orderNo);
            } else {
                refundRepository.updateResult(refund.refundNo(), RefundStatus.FAILED, outcome.channelRefundId());
                orderRepository.releaseRefundAmount(orderNo, refund.amountCents());
                throw new PayException(PayErrorKind.UPSTREAM_FAILED, "退款失败：" + outcome.message());
            }
            refund = refundRepository
                    .findByOrderIdempotency(orderNo, request.idempotencyKey())
                    .orElse(refund);
        }
        return new PayDtos.RefundView(
                refund.refundNo(),
                orderNo,
                refund.amountCents(),
                refund.status().name(),
                refund.channelRefundId(),
                refund.createdAt());
    }

    /**
     * 本地模拟渠道确认支付；仅模拟订单可确认，真实订单不受影响。
     */
    public PayDtos.OrderView confirmMockPayment(String orderNo) {
        PaymentOrder order = orderRepository
                .findByOrderNo(orderNo)
                .orElseThrow(() -> new PayException(PayErrorKind.NOT_FOUND, "订单不存在"));
        if (order.channel() != ChannelCode.MOCK) {
            throw new PayException(PayErrorKind.STATE_CONFLICT, "非模拟订单不可本地确认");
        }
        if (order.status() == OrderStatus.CREATED) {
            boolean transitioned = orderRepository.markPaid(orderNo, "MOCK-" + orderNo, Instant.now());
            if (transitioned) {
                orderRepository.findByOrderNo(orderNo).ifPresent(notifier::notify);
            }
        }
        return OrderViews.of(orderRepository.findByOrderNo(orderNo).orElse(order));
    }

    private PaymentOrder loadOwned(long userId, String orderNo) {
        PaymentOrder order = orderRepository
                .findByOrderNo(orderNo)
                .orElseThrow(() -> new PayException(PayErrorKind.NOT_FOUND, "订单不存在"));
        if (userId > 0 && order.userId() != userId) {
            throw new PayException(PayErrorKind.NOT_FOUND, "订单不存在");
        }
        return order;
    }

    private static void validateCreate(PayDtos.CreateOrderRequest request) {
        if (request.userId() <= 0) {
            throw new PayException(PayErrorKind.VALIDATION_FAILED, "缺少用户标识");
        }
        if (request.planCode() == null || request.planCode().isBlank()) {
            throw new PayException(PayErrorKind.VALIDATION_FAILED, "缺少套餐编码");
        }
        if (request.amountCents() <= 0) {
            throw new PayException(PayErrorKind.VALIDATION_FAILED, "订单金额必须大于 0");
        }
        if (request.idempotencyKey() == null || request.idempotencyKey().isBlank()) {
            throw new PayException(PayErrorKind.VALIDATION_FAILED, "缺少幂等键");
        }
    }

    private static String subject(PayDtos.CreateOrderRequest request) {
        return request.subject() == null || request.subject().isBlank() ? "OfferCoach 套餐" : request.subject();
    }

    private static String currency(PayDtos.CreateOrderRequest request) {
        return request.currency() == null || request.currency().isBlank()
                ? "CNY"
                : request.currency().strip().toUpperCase();
    }
}
