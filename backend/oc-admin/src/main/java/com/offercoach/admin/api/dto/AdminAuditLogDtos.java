package com.offercoach.admin.api.dto;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 审计日志响应；before/after 原样返回脱敏 JSONB。
 */
public final class AdminAuditLogDtos {

    private AdminAuditLogDtos() {}

    public record AuditLogResponse(
            String id,
            String adminUserId,
            String action,
            String targetType,
            String targetId,
            JsonNode before,
            JsonNode after,
            String requestId,
            String createdAt) {}
}
