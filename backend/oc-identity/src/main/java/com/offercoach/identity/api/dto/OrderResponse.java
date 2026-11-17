package com.offercoach.identity.api.dto;

import com.offercoach.pay.api.PayDtos;
import java.time.Instant;

/**
 * 订单响应；orderId 字符串化以匹配前端 string 口径。
 */
public record OrderResponse(
        String orderId,
        String orderNo,
        String planCode,
        int amountCents,
        String currency,
        String status,
        String channel,
        String codeUrl,
        Instant expiresAt,
        Instant paidAt,
        Instant createdAt) {

    public static OrderResponse from(PayDtos.OrderView view) {
        return new OrderResponse(
                Long.toString(view.orderId()),
                view.orderNo(),
                view.planCode(),
                view.amountCents(),
                view.currency(),
                view.status(),
                view.channel(),
                view.codeUrl(),
                view.expiresAt(),
                view.paidAt(),
                view.createdAt());
    }
}
