package com.offercoach.identity.domain.quota;

/**
 * subscription_plan 行的领域投影；featuresJson 为 JSONB 文本形式。
 */
public record SubscriptionPlan(
        String code,
        String name,
        int priceCents,
        int periodDays,
        int interviewCredits,
        int resumeCredits,
        String featuresJson,
        boolean active) {}
