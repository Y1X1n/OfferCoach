package com.offercoach.admin.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 数据保留策略配置。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "oc.retention")
public class RetentionProperties {

    /** AI 调用日志保留天数，默认 90 天。 */
    private int aiCallDays = 90;

    /** 管理端审计日志保留天数，默认 1095 天（3 年）。 */
    private int auditDays = 1095;
}
