package com.offercoach.admin.api.dto;

/**
 * AI 调用日志响应；只读。
 */
public final class AdminAiCallDtos {

    private AdminAiCallDtos() {}

    public record AiCallResponse(
            String id,
            String userId,
            String scene,
            String provider,
            String model,
            String status,
            Integer tokensIn,
            Integer tokensOut,
            Integer firstTokenMs,
            Integer latencyMs,
            String errorCode,
            String sessionId,
            String requestId,
            String traceId,
            String promptVersion,
            String createdAt) {}
}
