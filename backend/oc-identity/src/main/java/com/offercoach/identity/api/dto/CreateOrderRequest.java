package com.offercoach.identity.api.dto;

/**
 * POST /api/v1/orders 请求体。
 */
public record CreateOrderRequest(String planCode, String channel) {}
