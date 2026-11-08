package com.offercoach.admin.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A/B 实验请求与响应。
 */
public final class AdminExperimentDtos {

    private AdminExperimentDtos() {}

    public record ExperimentResponse(
            String id,
            String templateCode,
            String name,
            String status,
            int trafficPercent,
            JsonNode variants,
            JsonNode guardrails,
            String startAt,
            String endAt,
            String createdAt) {}

    public record CreateExperimentRequest(
            @NotBlank String templateCode,
            @NotBlank @Size(max = 120) String name,
            @Min(0) @Max(100) Integer trafficPercent,
            @NotNull JsonNode variants,
            JsonNode guardrails) {}

    public record StatusRequest(@NotBlank String status) {}
}
