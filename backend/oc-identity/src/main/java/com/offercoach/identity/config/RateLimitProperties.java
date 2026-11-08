package com.offercoach.identity.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 登录/注册限流与失败锁定配置，前缀 oc.rate-limit；未配置时使用默认值。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "oc.rate-limit")
public class RateLimitProperties {

    /** 单 IP 每分钟允许的登录尝试次数。 */
    private int loginPerMinute = 10;

    /** 单 IP 每分钟允许的注册尝试次数。 */
    private int registerPerMinute = 3;

    /** 同一账号在窗口期内的失败阈值，达到后锁定。 */
    private int failThreshold = 5;

    /** 账号失败计数窗口，同时作为锁定窗口。 */
    private Duration failWindow = Duration.ofMinutes(15);
}
