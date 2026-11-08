package com.offercoach.admin.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Prompt 管理请求与响应。
 */
public final class AdminPromptDtos {

    private AdminPromptDtos() {}

    public record TemplateResponse(
            String id,
            String code,
            String name,
            String scene,
            String description,
            String activeVersion,
            String createdAt,
            String updatedAt) {}

    public record CreateTemplateRequest(
            @NotBlank @Pattern(regexp = "[a-z][a-z0-9_]{0,63}") String code,
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 32) String scene,
            String description) {}

    public record VersionResponse(
            String id,
            String templateCode,
            String version,
            String status,
            String content,
            JsonNode variablesSchema,
            JsonNode modelConfig,
            String contentHash,
            String createdBy,
            String publishedAt,
            String createdAt) {}

    public record CreateVersionRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{1,16}") String version,
            @NotBlank String content,
            JsonNode variablesSchema,
            JsonNode modelConfig) {}

    public record ActiveVersionResponse(
            String templateCode,
            String version,
            String content,
            JsonNode variablesSchema,
            JsonNode modelConfig,
            String contentHash,
            String publishedAt) {}
}
