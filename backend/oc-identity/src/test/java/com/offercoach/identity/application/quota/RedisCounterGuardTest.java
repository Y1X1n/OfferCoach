package com.offercoach.identity.application.quota;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.offercoach.common.error.BizException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * 统一计数守卫：上限语义、超限错误、Redis 故障放行与归还。
 */
class RedisCounterGuardTest {

    private static final Duration TTL = Duration.ofDays(1);
    private static final String TTL_MILLIS = Long.toString(TTL.toMillis());
    private static final String KEY = "oc:test:counter";

    private StringRedisTemplate redis;
    private RedisCounterGuard guard;

    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        guard = new RedisCounterGuard(redis);
    }

    @SuppressWarnings("unchecked")
    private void stubCounter(Long value) {
        when(redis.execute(any(RedisScript.class), anyList(), any())).thenReturn(value);
    }

    @Test
    void unlimitedWhenLimitIsZero() {
        assertThat(guard.tryAcquire(KEY, 0, TTL)).isTrue();

        verifyNoInteractions(redis);
    }

    @Test
    void acquiresWhenUnderLimit() {
        stubCounter(1L);

        assertThat(guard.tryAcquire(KEY, 3, TTL)).isTrue();
    }

    @Test
    void passesTtlMillisToScript() {
        stubCounter(1L);

        guard.tryAcquire(KEY, 3, TTL);

        verifyScriptArgs(TTL_MILLIS);
    }

    @Test
    void rejectsWhenOverLimit() {
        stubCounter(4L);

        assertThat(guard.tryAcquire(KEY, 3, TTL)).isFalse();
    }

    @Test
    void throwsWithScopeWhenOverLimit() {
        stubCounter(4L);

        assertThatThrownBy(() -> guard.tryAcquireOrThrow(KEY, 3, TTL, "SEARCH"))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).details())
                        .containsEntry("limit", 3)
                        .containsEntry("scope", "SEARCH"));
    }

    @Test
    void failsOpenWhenRedisUnavailable() {
        when(redis.execute(any(RedisScript.class), anyList(), any()))
                .thenThrow(new IllegalStateException("redis down"));

        assertThat(guard.tryAcquire(KEY, 3, TTL)).isTrue();
    }

    @Test
    void releaseRunsAtomically() {
        stubCounter(0L);

        guard.release(KEY);

        verifyScriptArgs();
    }

    @Test
    void releaseIgnoresRedisFailure() {
        when(redis.execute(any(RedisScript.class), anyList(), any()))
                .thenThrow(new IllegalStateException("redis down"));

        guard.release(KEY);
    }

    @SuppressWarnings("unchecked")
    private void verifyScriptArgs(String... args) {
        org.mockito.Mockito.verify(redis).execute(any(RedisScript.class), eq(java.util.List.of(KEY)), args);
    }
}
