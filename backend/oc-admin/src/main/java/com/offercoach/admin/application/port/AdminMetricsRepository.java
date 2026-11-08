package com.offercoach.admin.application.port;

import com.offercoach.admin.application.model.AdminModels.Metrics;

/**
 * 运营指标聚合；时间窗口以天数传入，SQL 使用 make_interval 参数化。
 */
public interface AdminMetricsRepository {

    Metrics overview(int rangeDays);
}
