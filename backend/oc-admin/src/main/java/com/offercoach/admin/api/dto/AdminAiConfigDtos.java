package com.offercoach.admin.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
            ToolSwitches tools,
            String updatedAt,
            String updatedBy) {}

    /**
     * 工具能力开关与朗读音色（响应与请求共用）。
     */
    public record ToolSwitches(
            boolean asrEnabled,
            boolean ttsEnabled,
            boolean ocrEnabled,
            boolean searchEnabled,
            boolean fetchEnabled,
            @Pattern(regexp = "stella|tide", message = "音色仅支持 stella 或 tide") String ttsDefaultVoice) {}

    public record UpdateAiConfigRequest(
            @NotNull Boolean enabled,
            @NotBlank @Size(max = 512) String baseUrl,
            @NotBlank @Size(max = 128) String model,
            @Size(max = 128) String smallModel,
            @Size(max = 512) String apiKey,
            Boolean clearApiKey,
            Boolean asrEnabled,
            Boolean ttsEnabled,
            Boolean ocrEnabled,
            Boolean searchEnabled,
            Boolean fetchEnabled,
            @Pattern(regexp = "stella|tide", message = "音色仅支持 stella 或 tide") String ttsDefaultVoice) {}

    public record AiConfigTestResponse(boolean ok, String model, long latencyMs, String message) {}
}
