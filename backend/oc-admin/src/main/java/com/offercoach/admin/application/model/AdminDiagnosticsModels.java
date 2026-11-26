package com.offercoach.admin.application.model;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 管理端运行诊断视图：只读，不返回回答正文与简历原文。
 */
public final class AdminDiagnosticsModels {

    private AdminDiagnosticsModels() {}

    public record ReportStats(
            long pendingReports,
            long oldestPendingSeconds,
            long failedReports24h,
            long stuckSessions,
            long staleAnswers,
            long aiFailures24h) {}

    public record ReportTask(
            long id,
            long sessionId,
            long userId,
            int attempts,
            String lastError,
            long ageSeconds,
            OffsetDateTime updatedAt) {}

    public record SessionSnapshot(
            long id,
            long userId,
            String status,
            int questionCount,
            int answeredCount,
            int currentSeq,
            Double totalScore,
            String failureReason,
            String resumeTitle,
            String jdTitle,
            OffsetDateTime createdAt,
            OffsetDateTime lastActiveAt) {}

    public record QuestionSnapshot(
            long id, int seq, String dimension, String difficulty, String source, String status) {}

    public record AnswerSnapshot(
            long id, long questionId, String status, String inputMode, OffsetDateTime submittedAt) {}

    public record AiCallSnapshot(
            long id,
            String scene,
            String provider,
            String model,
            String status,
            Integer latencyMs,
            Integer tokensIn,
            Integer tokensOut,
            String errorCode,
            String requestId,
            String promptVersion,
            OffsetDateTime createdAt) {}

    public record SessionDiagnostics(
            SessionSnapshot session,
            List<QuestionSnapshot> questions,
            List<AnswerSnapshot> answers,
            ReportTask report,
            List<AiCallSnapshot> aiCalls) {}
}
