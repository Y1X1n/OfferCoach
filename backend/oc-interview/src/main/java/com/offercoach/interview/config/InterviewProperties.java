package com.offercoach.interview.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 面试与文件存储配置，前缀 oc.interview / oc.storage。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "oc.interview")
public class InterviewProperties {

    private int minAnswerLength = 10;

    private int maxAnswerLength = 4000;

    private Duration sessionIdleAbandon = Duration.ofHours(24);

    private int reportMaxAttempts = 3;

    /** 语音作答音频保留天数（定时清理，0 表示不自动删除）。 */
    private int voiceRetentionDays = 7;

    /** JD 场景配置。 */
    private final Jd jd = new Jd();

    @Getter
    @Setter
    public static class Jd {

        /** 每用户每日 AI 调用上限（结构化与匹配共用，0 表示不限）。 */
        private int aiDailyLimit = 20;
    }
}
