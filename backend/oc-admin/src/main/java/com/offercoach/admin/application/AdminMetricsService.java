package com.offercoach.admin.application;

import com.offercoach.admin.application.model.AdminModels.Metrics;
import com.offercoach.admin.application.port.AdminMetricsRepository;
import com.offercoach.admin.application.support.MetricsRangeParser;
import org.springframework.stereotype.Service;

/**
 * 运营核心指标：range 只允许 1d/7d/30d。
 */
@Service
public class AdminMetricsService {

    private final AdminMetricsRepository metricsRepository;

    public AdminMetricsService(AdminMetricsRepository metricsRepository) {
        this.metricsRepository = metricsRepository;
    }

    public Metrics overview(String range) {
        return metricsRepository.overview(MetricsRangeParser.days(range));
    }
}
