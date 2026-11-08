package com.offercoach.admin.api.dto;

import java.math.BigDecimal;

/**
 * 核心指标响应；平均分保留一位小数，无数据时为 0。
 */
public final class AdminMetricsDtos {

    private AdminMetricsDtos() {}

    public record OverviewResponse(
            long totalUsers,
            long newUsers,
            long sessions,
            long completedSessions,
            long reports,
            BigDecimal averageScore,
            long aiCalls,
            long aiFailures,
            long activeUsersToday,
            String range) {}
}
