package com.offercoach.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * AI 并发闸门与熔断语义。
 *
 * <p>目标不是"限流"，而是供应商挂起时不要用 60 秒超时把 Tomcat 线程池吃光：
 * 拿不到名额就立刻抛 {@link AiUnavailableException} 让业务降级。</p>
 */
class AiCallGuardTest {

    private static AiProperties properties(int maxConcurrent, int threshold, Duration open, Duration queueWait) {
        AiProperties properties = new AiProperties();
        properties.setMaxConcurrentCalls(maxConcurrent);
        properties.setBreakerFailureThreshold(threshold);
        properties.setBreakerOpenDuration(open);
        properties.setQueueWait(queueWait);
        return properties;
    }

    @Test
    void rejectsWhenConcurrencyLimitReached() {
        AiCallGuard guard = new AiCallGuard(properties(1, 10, Duration.ofSeconds(30), Duration.ZERO));

        AiCallGuard.Permit held = guard.acquire();
        assertThatThrownBy(guard::acquire)
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("并发已满");

        held.close();
        assertThatCode(() -> guard.acquire().close()).doesNotThrowAnyException();
    }

    @Test
    void opensAfterConsecutiveUpstreamFailures() {
        AiCallGuard guard = new AiCallGuard(properties(8, 3, Duration.ofSeconds(30), Duration.ZERO));

        for (int index = 0; index < 3; index++) {
            try (AiCallGuard.Permit permit = guard.acquire()) {
                permit.failure();
            }
        }

        assertThat(guard.isOpen()).isTrue();
        assertThatThrownBy(guard::acquire)
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("熔断");
    }

    /**
     * 请求本身有问题（4xx、解析失败）不该计数：否则一条坏 Prompt 就能把全站 AI 熔断 30 秒。
     */
    @Test
    void neutralOutcomesDoNotTripBreaker() {
        AiCallGuard guard = new AiCallGuard(properties(8, 2, Duration.ofSeconds(30), Duration.ZERO));

        for (int index = 0; index < 5; index++) {
            guard.acquire().close();
        }

        assertThat(guard.isOpen()).isFalse();
    }

    @Test
    void successResetsFailureStreak() {
        AiCallGuard guard = new AiCallGuard(properties(8, 3, Duration.ofSeconds(30), Duration.ZERO));

        try (AiCallGuard.Permit first = guard.acquire()) {
            first.failure();
        }
        try (AiCallGuard.Permit second = guard.acquire()) {
            second.failure();
        }
        try (AiCallGuard.Permit success = guard.acquire()) {
            success.success();
        }
        // 计数已清零，再失败两次（阈值 3）不应熔断。
        for (int index = 0; index < 2; index++) {
            try (AiCallGuard.Permit permit = guard.acquire()) {
                permit.failure();
            }
        }

        assertThat(guard.isOpen()).isFalse();
    }

    @Test
    void allowsCallsAgainAfterOpenDuration() throws InterruptedException {
        AiCallGuard guard = new AiCallGuard(properties(8, 1, Duration.ofMillis(50), Duration.ZERO));

        try (AiCallGuard.Permit permit = guard.acquire()) {
            permit.failure();
        }
        assertThat(guard.isOpen()).isTrue();

        Thread.sleep(120);

        assertThat(guard.isOpen()).isFalse();
        assertThatCode(() -> guard.acquire().close()).doesNotThrowAnyException();
    }

    /**
     * 熔断配置为 0 表示关闭该能力，只保留并发保护。
     */
    @Test
    void thresholdZeroDisablesBreaker() {
        AiCallGuard guard = new AiCallGuard(properties(8, 0, Duration.ofSeconds(30), Duration.ZERO));

        for (int index = 0; index < 20; index++) {
            try (AiCallGuard.Permit permit = guard.acquire()) {
                permit.failure();
            }
        }

        assertThat(guard.isOpen()).isFalse();
    }
}
