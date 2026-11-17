package com.offercoach.pay.service.domain;

/**
 * 订单状态；与 {@code payment_order.status} 的 CHECK 值一致。
 *
 * <p>CREATED 表示已下单待支付；PAID 已支付；CLOSED 已关闭（超时/取消）；REFUNDED 已退款；FAILED 失败。</p>
 */
public enum OrderStatus {
    CREATED,
    PAID,
    FAILED,
    REFUNDED,
    CLOSED
}
