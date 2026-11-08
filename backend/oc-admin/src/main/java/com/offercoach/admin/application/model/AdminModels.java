package com.offercoach.admin.application.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Function;

/**
 * 管理端应用层视图与分页模型；持久化层与 API 层都只依赖本类中的只读结构。
 */
public final class AdminModels {

    private AdminModels() {}

    /**
     * 分页结果；页码与大小已按管理端约定收敛到 1 起始、1-100。
     */
    public record Paged<T>(List<T> items, long total, int page, int size) {

        public <R> Paged<R> map(Function<T, R> mapper) {
            return new Paged<>(items.stream().map(mapper).toList(), total, page, size);
        }
    }

    /**
     * 题库列表视图；tags 已由 chr(31) 拼接字符串还原为列表。
     */
    public record Question(
            long id,
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
            OffsetDateTime createdAt) {}

    /**
     * 题库写入字段；null 表示 PATCH 时保持不变，创建时由服务补默认值。
     */
    public record QuestionWrite(
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

    /**
     * 管理端导入条目；仅承载结构化字段，校验由 QuestionImportValidator 完成。
     */
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

    /**
     * 已校验并标准化的导入条目；sourcePath 由服务按批次生成。
     */
    public record ImportedQuestion(
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

    /**
     * 导入批次统计。
     */
    public record ImportBatch(
            long id,
            String sourceRepo,
            int totalCount,
            int successCount,
            int failedCount,
            int skippedCount,
            String status,
            JsonNode errorReport,
            Long createdBy,
            OffsetDateTime createdAt,
            OffsetDateTime finishedAt) {}

    /**
     * 管理端用户视图；planCode 来自 user_quota，缺省回退 FREE。
     */
    public record User(
            long id,
            String email,
            String nickname,
            String role,
            String status,
            String planCode,
            Integer interviewCredits,
            Integer resumeCredits,
            OffsetDateTime createdAt,
            OffsetDateTime lastLoginAt) {}

    /**
     * 额度调整结果。
     */
    public record QuotaAdjustment(String type, int delta, int balanceAfter, boolean replayed) {}

    /**
     * 核心指标快照；时间窗口由 range 参数决定。
     */
    public record Metrics(
            long totalUsers,
            long newUsers,
            long sessions,
            long completedSessions,
            long reports,
            BigDecimal averageScore,
            long aiCalls,
            long aiFailures,
            long activeUsersToday) {}

    /**
     * AI 调用日志视图；只读。
     */
    public record AiCall(
            long id,
            Long userId,
            String scene,
            String provider,
            String model,
            String status,
            Integer tokensIn,
            Integer tokensOut,
            Integer firstTokenMs,
            Integer latencyMs,
            String errorCode,
            OffsetDateTime createdAt) {}

    /**
     * 审计日志视图；before/after 已解析为 JSON。
     */
    public record AuditLog(
            long id,
            long adminUserId,
            String action,
            String targetType,
            String targetId,
            JsonNode before,
            JsonNode after,
            String requestId,
            OffsetDateTime createdAt) {}

    /**
     * Prompt 模板列表视图，含当前发布版本。
     */
    public record PromptTemplate(
            long id,
            String code,
            String name,
            String scene,
            String description,
            String activeVersion,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {}

    /**
     * Prompt 版本视图；content 一经发布不可修改。
     */
    public record PromptVersion(
            long id,
            String templateCode,
            String version,
            String status,
            String content,
            JsonNode variablesSchema,
            JsonNode modelConfig,
            String contentHash,
            Long createdBy,
            OffsetDateTime publishedAt,
            OffsetDateTime createdAt) {}

    /**
     * A/B 实验视图；仅做管理端最小 CRUD，不接入运行时。
     */
    public record Experiment(
            long id,
            String templateCode,
            String name,
            String status,
            int trafficPercent,
            JsonNode variants,
            JsonNode guardrails,
            OffsetDateTime startAt,
            OffsetDateTime endAt,
            OffsetDateTime createdAt) {}
}
