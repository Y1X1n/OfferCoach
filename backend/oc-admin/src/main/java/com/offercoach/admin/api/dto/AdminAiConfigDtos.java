package com.offercoach.admin.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 管理端 AI 运行时配置请求与响应；响应永不包含 API Key 明文。
 */
public final class AdminAiConfigDtos {

    private AdminAiConfigDtos() {}

    public record AiConfigResponse(
            boolean enabled,
            String baseUrl,
            String model,
            String smallModel,
            boolean hasApiKey,
            String apiKeyMasked,
            String updatedAt,
            String updatedBy) {}

    public record UpdateAiConfigRequest(
            @NotNull Boolean enabled,
            @NotBlank @Size(max = 512) String baseUrl,
            @NotBlank @Size(max = 128) String model,
            @Size(max = 128) String smallModel,
            @Size(max = 512) String apiKey,
            Boolean clearApiKey) {}

    public record AiConfigTestResponse(boolean ok, String model, long latencyMs, String message) {}
}
