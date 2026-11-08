package com.offercoach.identity.application.quota;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 套餐展示视图。
 */
public record PlanView(
        String code,
        String name,
        int priceCents,
        int periodDays,
        int interviewCredits,
        int resumeCredits,
        Map<String, Object> features,
        boolean active) {

    public PlanView {
        features = features == null || features.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(features));
    }
}
