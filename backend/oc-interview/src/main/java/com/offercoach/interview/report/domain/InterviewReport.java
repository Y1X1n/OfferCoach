package com.offercoach.interview.report.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 面试报告；JSONB 字段在仓储边界解析为结构化列表。
 */
public record InterviewReport(
        long id,
        long sessionId,
        long userId,
        String status,
        BigDecimal overallScore,
        List<RadarItem> radar,
        String summary,
        List<String> highlights,
        List<String> risks,
        List<ImprovementItem> improvementPlan,
        List<BreakdownItem> questionBreakdown,
        int attempts,
        String lastError,
        OffsetDateTime generatedAt,
        OffsetDateTime createdAt) {

    public record RadarItem(String dimension, String label, double score) {}

    public record ImprovementItem(int priority, String action, String reason, String resource) {}

    public record BreakdownItem(
            long questionId, long answerId, long feedbackId, int seq, double score, String dimension, String summary) {}
}
