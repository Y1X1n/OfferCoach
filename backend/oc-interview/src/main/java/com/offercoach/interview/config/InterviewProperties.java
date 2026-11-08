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
}
