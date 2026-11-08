package com.offercoach.admin.api;

import com.offercoach.admin.api.dto.AdminMetricsDtos.OverviewResponse;
import com.offercoach.admin.application.AdminMetricsService;
import com.offercoach.admin.application.model.AdminModels.Metrics;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端核心指标端点。
 */
@RestController
@RequestMapping("/api/v1/admin/metrics")
@PreAuthorize("hasRole('ADMIN')")
public class AdminMetricsController {

    private final AdminMetricsService metricsService;

    public AdminMetricsController(AdminMetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @GetMapping("/overview")
    public OverviewResponse overview(@RequestParam(value = "range", defaultValue = "7d") String range) {
        Metrics metrics = metricsService.overview(range);
        return new OverviewResponse(
                metrics.totalUsers(),
                metrics.newUsers(),
                metrics.sessions(),
                metrics.completedSessions(),
                metrics.reports(),
                metrics.averageScore(),
                metrics.aiCalls(),
                metrics.aiFailures(),
                metrics.activeUsersToday(),
                range);
    }
}
