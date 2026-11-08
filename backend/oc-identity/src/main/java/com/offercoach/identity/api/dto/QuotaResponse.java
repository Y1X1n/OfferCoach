package com.offercoach.identity.api.dto;

import com.offercoach.identity.application.quota.QuotaSnapshot;
import java.time.LocalDate;
import java.util.Map;

/**
 * GET /api/v1/quota 响应体。
 */
public record QuotaResponse(
        String planCode,
        String planName,
        int interviewCredits,
        int resumeCredits,
        LocalDate periodEnd,
        Daily daily,
        Map<String, Object> features) {

    public record Daily(LocalDate date, int sessionsUsed, int sessionsLimit) {}

    public static QuotaResponse from(QuotaSnapshot snapshot) {
        return new QuotaResponse(
                snapshot.planCode(),
                snapshot.planName(),
                snapshot.interviewCredits(),
                snapshot.resumeCredits(),
                snapshot.periodEnd(),
                new Daily(snapshot.dailyDate(), snapshot.sessionsUsed(), snapshot.sessionsLimit()),
                snapshot.features());
    }
}
