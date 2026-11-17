package com.offercoach.pay.service.domain;

import java.time.Instant;

/**
 * 支付订单领域模型；与 {@code payment_order} 表映射。
 */
public record PaymentOrder(
        long id,
        String orderNo,
        long userId,
        String planCode,
        String subject,
        int amountCents,
        String currency,
        ChannelCode channel,
        OrderStatus status,
        String providerTxnId,
        String prepayId,
        String codeUrl,
        String idempotencyKey,
        String notifyStatus,
        int notifyAttempts,
        Instant expiresAt,
        Instant paidAt,
        Instant closedAt,
        int refundedCents,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * 是否仍可支付（未支付且未过期）。
     */
    public boolean payable(Instant now) {
        return status == OrderStatus.CREATED && (expiresAt == null || expiresAt.isAfter(now));
    }
}
