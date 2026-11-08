package com.offercoach.identity.application.quota;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 当前用户额度的只读快照，供账号 / 面试模块与查询接口使用。
 */
public record QuotaSnapshot(
        String planCode,
        String planName,
        int interviewCredits,
        int resumeCredits,
        LocalDate periodEnd,
        LocalDate dailyDate,
        int sessionsUsed,
        int sessionsLimit,
        Map<String, Object> features) {

    public QuotaSnapshot {
        features = features == null || features.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(features));
    }
}
