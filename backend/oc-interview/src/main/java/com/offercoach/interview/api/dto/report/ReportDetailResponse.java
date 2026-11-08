package com.offercoach.interview.api.dto.report;

import java.math.BigDecimal;
import java.util.List;

/**
 * 报告详情，结构与 api.md §8.1 一致。
 */
public record ReportDetailResponse(
        String id,
        String sessionId,
        String title,
        String company,
        String role,
        int questionCount,
        String status,
        BigDecimal overallScore,
        List<RadarItem> radar,
        String summary,
        List<String> highlights,
        List<String> risks,
        List<ImprovementItem> improvementPlan,
        List<BreakdownItem> questionBreakdown,
        String generatedAt,
        String createdAt) {

    public record RadarItem(String dimension, String label, double score) {}

    public record ImprovementItem(int priority, String action, String reason, String resource) {}

    public record BreakdownItem(
            String questionId,
            String answerId,
            String feedbackId,
            int seq,
            double score,
            String dimension,
            String summary,
            String question,
            String answer,
            FeedbackView feedback) {}

    /** 逐题反馈摘要：供报告页展示原文证据与改写建议。 */
    public record FeedbackView(
            java.math.BigDecimal overallScore,
            java.util.Map<String, java.math.BigDecimal> dimensions,
            java.util.List<String> strengths,
            java.util.List<String> improvements,
            java.util.List<EvidenceView> evidence,
            String rewrittenAnswer) {}

    public record EvidenceView(String quote, String reason) {}
}
