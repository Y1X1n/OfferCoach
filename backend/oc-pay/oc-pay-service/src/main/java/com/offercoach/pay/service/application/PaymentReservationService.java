package com.offercoach.pay.service.application;

import com.offercoach.pay.api.PayDtos;
import com.offercoach.pay.service.application.port.PaymentOrderRepository;
import com.offercoach.pay.service.application.port.PaymentRefundRepository;
import com.offercoach.pay.service.domain.OrderStatus;
import com.offercoach.pay.service.domain.PaymentOrder;
import com.offercoach.pay.service.domain.PaymentRefund;
import com.offercoach.pay.service.domain.RefundStatus;
import com.offercoach.pay.service.error.PayErrorKind;
import com.offercoach.pay.service.error.PayException;
import com.offercoach.pay.service.support.OrderNumbers;
import java.time.Instant;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 退款预占：在事务内锁定订单、校验剩余可退金额并落退款单，保证并发退款不超额。
 *
 * <p>渠道调用（长 HTTP）在事务外执行；同一幂等键重复请求返回同一退款单。</p>
 */
@Service
public class PaymentReservationService {

    private final PaymentOrderRepository orderRepository;
    private final PaymentRefundRepository refundRepository;

    public PaymentReservationService(PaymentOrderRepository orderRepository, PaymentRefundRepository refundRepository) {
        this.orderRepository = orderRepository;
        this.refundRepository = refundRepository;
    }

    @Transactional
    public Reservation reserveRefund(long userId, String orderNo, PayDtos.RefundRequest request) {
        if (request.amountCents() <= 0) {
            throw new PayException(PayErrorKind.VALIDATION_FAILED, "退款金额必须大于 0");
        }
        if (request.idempotencyKey() == null || request.idempotencyKey().isBlank()) {
            throw new PayException(PayErrorKind.VALIDATION_FAILED, "退款幂等键不能为空");
        }
        PaymentOrder order = orderRepository
                .lockByOrderNo(orderNo)
                .orElseThrow(() -> new PayException(PayErrorKind.NOT_FOUND, "订单不存在"));
        if (userId > 0 && order.userId() != userId) {
            throw new PayException(PayErrorKind.NOT_FOUND, "订单不存在");
        }
        Optional<PaymentRefund> existing = refundRepository.findByOrderIdempotency(orderNo, request.idempotencyKey());
        if (existing.isPresent()) {
            return new Reservation(order, existing.get(), false);
        }
        if (order.status() != OrderStatus.PAID && order.status() != OrderStatus.REFUNDED) {
            throw new PayException(PayErrorKind.STATE_CONFLICT, "仅已支付订单可退款");
        }
        int remaining = order.amountCents() - order.refundedCents();
        if (request.amountCents() > remaining) {
            throw new PayException(PayErrorKind.VALIDATION_FAILED, "退款金额超过剩余可退金额");
        }
        Instant now = Instant.now();
        PaymentRefund refund = new PaymentRefund(
                0L,
                OrderNumbers.refundNo(),
                orderNo,
                order.userId(),
                request.amountCents(),
                RefundStatus.CREATED,
                null,
                request.reason(),
                request.idempotencyKey(),
                now,
                now);
        try {
            refundRepository.insert(refund);
        } catch (DuplicateKeyException exception) {
            PaymentRefund concurrent = refundRepository
                    .findByOrderIdempotency(orderNo, request.idempotencyKey())
                    .orElseThrow(() -> new PayException(PayErrorKind.STATE_CONFLICT, "退款请求冲突，请重试"));
            return new Reservation(order, concurrent, false);
        }
        return new Reservation(order, refund, true);
    }

    /**
     * 退款预占结果；{@code created} 为 true 时调用方需完成渠道退款。
     */
    public record Reservation(PaymentOrder order, PaymentRefund refund, boolean created) {}
}
