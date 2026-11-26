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

    /**
     * 支付回调日志保留天数，默认 180 天。
     *
     * <p>该表只记录回调原文与处理结果，用于排查对账差异；账务事实在 {@code payment_order} /
     * {@code payment_refund} / {@code quota_ledger} 中长期保留，所以这里可以按窗口清理。</p>
     */
    private int paymentNotifyDays = 180;
}
