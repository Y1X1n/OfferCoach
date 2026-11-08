package com.offercoach.admin.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 用户与额度管理请求与响应。
 */
public final class AdminUserDtos {

    private AdminUserDtos() {}

    public record UserResponse(
            String id,
            String email,
            String nickname,
            String role,
            String status,
            String planCode,
            Integer interviewCredits,
            Integer resumeCredits,
            String createdAt,
            String lastLoginAt) {}

    public record StatusRequest(@NotBlank String status) {}

    public record RoleRequest(@NotBlank String role) {}

    public record ResetPasswordRequest(@NotBlank String password) {}

    public record QuotaRequest(
            @NotBlank String type,
            @NotNull Integer delta,
            @NotBlank @Size(max = 32) String reason,
            @NotBlank @Size(max = 128) String idempotencyKey) {}

    public record QuotaResponse(String userId, String type, int delta, int balanceAfter, boolean replayed) {}
}
