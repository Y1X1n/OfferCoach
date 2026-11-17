package com.offercoach.pay.service.application;

import com.offercoach.pay.api.PayDtos;
import com.offercoach.pay.service.domain.PaymentOrder;

/**
 * 领域模型到对外视图的映射。
 */
public final class OrderViews {

    private OrderViews() {}

    public static PayDtos.OrderView of(PaymentOrder order) {
        return new PayDtos.OrderView(
                order.id(),
                order.orderNo(),
                order.userId(),
                order.planCode(),
                order.subject(),
                order.amountCents(),
                order.currency(),
                order.channel() == null ? null : order.channel().code(),
                order.channel() == null ? null : order.channel().method(),
                order.status() == null ? null : order.status().name(),
                order.codeUrl(),
                order.expiresAt(),
                order.paidAt(),
                order.refundedCents(),
                order.createdAt(),
                order.updatedAt());
    }
}
