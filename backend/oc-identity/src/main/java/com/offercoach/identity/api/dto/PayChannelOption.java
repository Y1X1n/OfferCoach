package com.offercoach.identity.api.dto;

/**
 * GET /api/v1/pay-channels 响应数组元素：单个支付渠道的可用性。
 */
public record PayChannelOption(String channel, boolean enabled, String method) {}
