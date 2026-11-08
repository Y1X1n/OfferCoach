package com.offercoach.identity.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 额度相关配置，对应 application.yml 中的 oc.quota。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "oc.quota")
public class QuotaProperties {

    /** FREE 套餐每月面试额度。 */
    private int freeMonthlyInterviews = 3;

    /** FREE 套餐每月简历解析额度。 */
    private int freeMonthlyResumes = 3;

    /** 每日会话次数上限。 */
    private int dailySessionCap = 3;
}
