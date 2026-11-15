package com.offercoach.identity.application.quota;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.offercoach.common.error.BizException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * 统一计数守卫：上限语义、超限错误、Redis 故障放行与归还。
 */
class RedisCounterGuardTest {

    private static final Duration TTL = Duration.ofDays(1);
    private static final String KEY = "oc:test:counter";

    private StringRedisTemplate redis;
    private ValueOperations<String, String> ops;
    private RedisCounterGuard guard;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        guard = new RedisCounterGuard(redis);
    }

    @Test
    void unlimitedWhenLimitIsZero() {
        assertThat(guard.tryAcquire(KEY, 0, TTL)).isTrue();

        verifyNoInteractions(ops);
    }

    @Test
    void acquiresWhenUnderLimit() {
        when(ops.increment(KEY)).thenReturn(1L);

        assertThat(guard.tryAcquire(KEY, 3, TTL)).isTrue();

        verify(ops).increment(KEY);
    }

    @Test
    void rejectsWhenOverLimit() {
        when(ops.increment(KEY)).thenReturn(4L);

        assertThat(guard.tryAcquire(KEY, 3, TTL)).isFalse();
    }

    @Test
    void throwsWithScopeWhenOverLimit() {
        when(ops.increment(KEY)).thenReturn(4L);

        assertThatThrownBy(() -> guard.tryAcquireOrThrow(KEY, 3, TTL, "SEARCH"))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).details())
                        .containsEntry("limit", 3)
                        .containsEntry("scope", "SEARCH"));
    }

    @Test
    void failsOpenWhenRedisUnavailable() {
        when(ops.increment(KEY)).thenThrow(new IllegalStateException("redis down"));

        assertThat(guard.tryAcquire(KEY, 3, TTL)).isTrue();
    }

    @Test
    void releaseDeletesKeyWhenItWouldGoNegative() {
        when(ops.decrement(KEY)).thenReturn(-1L);

        guard.release(KEY);

        verify(redis).delete(KEY);
        verify(ops, never()).set(eq(KEY), any(String.class));
    }

    @Test
    void releaseIgnoresRedisFailure() {
        when(ops.decrement(KEY)).thenThrow(new IllegalStateException("redis down"));

        guard.release(KEY);

        verify(ops, never()).set(eq(KEY), any(String.class));
    }
}
