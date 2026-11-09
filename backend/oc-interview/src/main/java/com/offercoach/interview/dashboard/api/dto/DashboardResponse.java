package com.offercoach.interview.dashboard.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * GET /api/v1/dashboard 响应：工作台首屏所需的聚合数据。
 */
public record DashboardResponse(
        Quota quota,
        boolean resumeReady,
        long jdCount,
        ActiveSession activeSession,
        List<Report> recentReports,
        long reportTotal,
        BigDecimal averageScore,
        int scoredReportCount) {

    public record Quota(
            String planCode,
            String planName,
            int interviewCredits,
            int resumeCredits,
            LocalDate periodEnd,
            Daily daily,
            Map<String, Object> features) {}

    public record Daily(LocalDate date, int sessionsUsed, int sessionsLimit) {}

    public record ActiveSession(
            String id,
            String status,
            String resumeId,
            String jdId,
            String resumeTitle,
            String jdTitle,
            String jdCompany,
            int questionCount,
            int answeredCount,
            String startedAt,
            String lastActiveAt) {}

    public record Report(
            String id,
            String sessionId,
            String status,
            BigDecimal overallScore,
            String summary,
            String title,
            String company,
            String role,
            int questionCount,
            String createdAt) {}
}
