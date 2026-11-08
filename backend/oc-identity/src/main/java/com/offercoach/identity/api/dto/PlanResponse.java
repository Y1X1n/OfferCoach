package com.offercoach.identity.api.dto;

import com.offercoach.identity.application.quota.PlanView;
import java.util.Map;

/**
 * GET /api/v1/plans 响应数组元素。
 */
public record PlanResponse(
        String code,
        String name,
        int priceCents,
        int periodDays,
        int interviewCredits,
        int resumeCredits,
        Map<String, Object> features,
        boolean active) {

    public static PlanResponse from(PlanView view) {
        return new PlanResponse(
                view.code(),
                view.name(),
                view.priceCents(),
                view.periodDays(),
                view.interviewCredits(),
                view.resumeCredits(),
                view.features(),
                view.active());
    }
}
