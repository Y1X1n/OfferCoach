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
}
