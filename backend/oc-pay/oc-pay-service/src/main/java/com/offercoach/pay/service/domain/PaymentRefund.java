package com.offercoach.pay.service.domain;

import java.time.Instant;

/**
 * 支付退款记录；与 {@code payment_refund} 表映射。
 */
public record PaymentRefund(
        long id,
        String refundNo,
        String orderNo,
        long userId,
        int amountCents,
        RefundStatus status,
        String channelRefundId,
        String reason,
        String idempotencyKey,
        Instant createdAt,
        Instant updatedAt) {}
