package com.offercoach.interview.api.dto.report;

import java.math.BigDecimal;

/**
 * 报告列表摘要。
 */
public record ReportSummaryResponse(
        String id,
        String sessionId,
        String status,
        BigDecimal overallScore,
        String summary,
        String title,
        String company,
        String role,
        int questionCount,
        String generatedAt,
        String createdAt) {}
