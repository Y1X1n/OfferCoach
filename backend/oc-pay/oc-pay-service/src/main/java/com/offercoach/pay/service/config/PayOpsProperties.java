package com.offercoach.pay.service.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 支付服务运行参数：对外回调地址、向 oc-app 发放额度的地址与重试策略。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "oc.pay.ops")
public class PayOpsProperties {

    /** 对外可访问的基地址，用于拼接渠道 notify_url / 支付宝 return_url；为空时渠道下单直接失败。 */
    private String publicBaseUrl = "";

    /** oc-app 基础地址，用于支付成功后回调发放额度。 */
    private String appBaseUrl = "http://127.0.0.1:8081";

    /** oc-app 额度发放接口路径。 */
    private String entitlementPath = "/internal/v1/payments/notify";

    /** 订单过期时长（分钟）；到期未支付由扫描任务关闭。 */
    private int orderExpireMinutes = 30;

    /** 额度发放通知最大尝试次数。 */
    private int notifyMaxAttempts = 8;

    /** 额度发放通知单次超时。 */
    private Duration notifyTimeout = Duration.ofSeconds(5);

    /** 回调时间戳允许的最大偏差（秒）；仅微信回调校验，防重放。 */
    private long notifyMaxSkewSeconds = 300;

    /** 过期订单无法确认渠道已关单时，继续保留待支付状态的最长宽限期；超过后置 FAILED 并进入人工队列。 */
    private Duration closeGrace = Duration.ofHours(24);

    /** 未支付订单过期与通知重试的扫描周期（cron，6 段）。 */
    private String sweepCron = "0 */3 * * * *";

    /** 支付异常队列巡检周期（cron，6 段）；存在 OPEN 异常时输出 ERROR 日志供告警。 */
    private String alertCron = "0 */30 * * * *";
}
