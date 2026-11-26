package com.offercoach.admin.application;

import com.offercoach.admin.application.model.AdminModels.AiUsage;
import com.offercoach.admin.application.model.AdminModels.Metrics;
import com.offercoach.admin.application.port.AdminMetricsRepository;
import com.offercoach.admin.application.support.MetricsRangeParser;
import com.offercoach.admin.config.AdminAiPricingProperties;
import org.springframework.stereotype.Service;

/**
 * 运营核心指标：range 只允许 1d/7d/30d；成本按模型 token 与配置单价估算。
 */
@Service
public class AdminMetricsService {

    private static final long MICROS_PER_UNIT = 1_000_000L;

    private final AdminMetricsRepository metricsRepository;
    private final AdminAiPricingProperties pricing;

    public AdminMetricsService(AdminMetricsRepository metricsRepository, AdminAiPricingProperties pricing) {
        this.metricsRepository = metricsRepository;
        this.pricing = pricing;
    }

    public Metrics overview(String range) {
        int days = MetricsRangeParser.days(range);
        Metrics base = metricsRepository.overview(days);
        AiUsage usage = metricsRepository.aiUsage(days);
        long costMicros = 0;
        for (var entry : usage.byModel().entrySet()) {
            AdminAiPricingProperties.ModelPrice price = pricing.priceOf(entry.getKey());
            costMicros += (entry.getValue().tokensIn() * price.inputMicrosPerMillion()
                            + entry.getValue().tokensOut() * price.outputMicrosPerMillion())
                    / MICROS_PER_UNIT;
        }
        return new Metrics(
                base.totalUsers(),
                base.newUsers(),
                base.sessions(),
                base.completedSessions(),
                base.reports(),
                base.averageScore(),
                base.aiCalls(),
                base.aiFailures(),
                usage.tokensIn(),
                usage.tokensOut(),
                costMicros,
                usage.latencyP95Ms(),
                base.activeUsersToday());
    }
}
