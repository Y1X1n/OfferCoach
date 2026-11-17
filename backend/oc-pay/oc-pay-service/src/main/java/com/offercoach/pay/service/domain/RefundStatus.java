package com.offercoach.pay.service.domain;

/**
 * 退款状态；与 {@code payment_refund.status} 的 CHECK 值一致。
 */
public enum RefundStatus {
    CREATED,
    SUCCESS,
    FAILED
}
