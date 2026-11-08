package com.offercoach.admin.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 题库管理请求与响应；枚举校验在服务层完成，导入条目逐条容错。
 */
public final class AdminQuestionDtos {

    private AdminQuestionDtos() {}

    public record QuestionResponse(
            String id,
            String title,
            String stem,
            String jobFamily,
            String dimension,
            String difficulty,
            String status,
            String source,
            String sourceRepo,
            String company,
            List<String> tags,
            String createdAt) {}

    public record CreateQuestionRequest(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 64) String jobFamily,
            @NotBlank String dimension,
            @NotBlank String difficulty,
            List<String> tags,
            @NotBlank String stem,
            String referenceAnswer,
            List<Object> scoringPoints,
            @Size(max = 120) String company,
            String sourceUrl,
            @Size(max = 64) String sourceLicense,
            @Size(max = 16) String language) {}

    public record UpdateQuestionRequest(
            @Size(max = 200) String title,
            @Size(max = 64) String jobFamily,
            String dimension,
            String difficulty,
            List<String> tags,
            String stem,
            String referenceAnswer,
            List<Object> scoringPoints,
            @Size(max = 120) String company,
            String sourceUrl,
            @Size(max = 64) String sourceLicense,
            @Size(max = 16) String language) {}

    public record ImportRequest(
            @NotBlank @Size(max = 160) String sourceRepo, @NotEmpty @Size(max = 500) List<ImportItem> items) {}

    public record ImportItem(
            String title,
            String jobFamily,
            String dimension,
            String difficulty,
            List<String> tags,
            String stem,
            String referenceAnswer,
            List<Object> scoringPoints,
            String company,
            String sourceUrl,
            String sourceLicense,
            String language) {}

    public record ImportBatchResponse(
            String id,
            String sourceRepo,
            int totalCount,
            int successCount,
            int failedCount,
            int skippedCount,
            String status,
            JsonNode errorReport,
            String createdBy,
            String createdAt,
            String finishedAt) {}
}
