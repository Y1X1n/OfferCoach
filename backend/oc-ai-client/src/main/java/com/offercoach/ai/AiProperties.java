package com.offercoach.ai;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OpenAI 兼容供应商配置；未配置密钥时视为不可用，由业务层走启发式降级。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "oc.ai")
public class AiProperties {

    private boolean enabled;

    private String baseUrl = "https://api.deepseek.com/v1";

    private String apiKey = "";

    private String model = "deepseek-chat";

    private String smallModel = "deepseek-chat";

    private Duration timeout = Duration.ofSeconds(60);

    private int maxTokens = 1800;

    private double temperature = 0.3;

    /**
     * 管理端保存 API Key 的 AES-GCM 根密钥；为空时只允许改 URL/模型，不能保存 Key。
     */
    private String secret = "";

    /** 是否允许 AI 网关指向内网/本机；生产默认关闭以降低 SSRF 风险。 */
    private boolean allowPrivateHosts;

    /** 每日全局 token 预算；0 表示不限制，超限后业务自动走确定性降级。 */
    private long dailyTokenBudget;

    /**
     * 同时在途的 AI 调用上限；超出后立即降级而不是继续占用请求线程。
     *
     * <p>默认 8：供应商挂起时最多占用 8 个 Tomcat 线程，其余请求立刻走启发式/模板降级，
     * 鉴权、额度、支付回调这些纯 DB 接口不受影响。</p>
     */
    private int maxConcurrentCalls = 8;

    /** 申请名额的最长等待；0 表示拿不到立即降级。 */
    private Duration queueWait = Duration.ofMillis(200);

    /** 连续多少次上游失败后熔断；0 表示不熔断。 */
    private int breakerFailureThreshold = 10;

    /** 熔断后跳过 AI 调用的时长；到点自动放行。 */
    private Duration breakerOpenDuration = Duration.ofSeconds(30);
}
