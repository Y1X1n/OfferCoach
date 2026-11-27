package com.offercoach.interview.jd.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.offercoach.common.cache.RedisKeys;
import com.offercoach.common.error.BizException;
import com.offercoach.common.error.ErrorCode;
import com.offercoach.common.time.BusinessDate;
import com.offercoach.identity.application.quota.RedisCounterGuard;
import com.offercoach.interview.config.InterviewProperties;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * JD AI 闸门：限额语义、超限错误、按用户按天计数与 Redis 故障放行。
 */
class JdAiGuardTest {

    private static final long USER_ID = 7L;
    private static final String TTL_MILLIS = Long.toString(Duration.ofDays(2).toMillis());
    /**
     * 必须与生产同源（{@link BusinessDate}，Asia/Shanghai）。
     *
     * <p>此前这里用 {@code LocalDate.now()}（JVM 默认时区，容器里是 UTC）：UTC 16:00–24:00 期间
     * 北京时间已跨日，期望键与实际键差一天，约 1/3 的运行会随机失败。</p>
     */
    private static final String DATE = BusinessDate.todayKey();

    private StringRedisTemplate redis;
    private InterviewProperties properties;
    private JdAiGuard guard;

    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        properties = new InterviewProperties();
        guard = new JdAiGuard(new RedisCounterGuard(redis), properties);
    }

    @Test
    void unlimitedWhenLimitIsZero() {
        properties.getJd().setAiDailyLimit(0);

        guard.checkAndCount(USER_ID);

        verifyNoInteractions(redis);
    }

    @Test
    void acquiresWhenUnderLimit() {
        stubCounter(1L);

        guard.checkAndCount(USER_ID);

        verify(redis).execute(any(RedisScript.class), eq(List.of(RedisKeys.dailyJdAi(USER_ID, DATE))), eq(TTL_MILLIS));
    }

    @Test
    void throwsRateLimitedWhenOverLimit() {
        stubCounter(21L);

        assertThatThrownBy(() -> guard.checkAndCount(USER_ID))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> {
                    BizException biz = (BizException) exception;
                    assertThat(biz.errorCode()).isEqualTo(ErrorCode.RATE_LIMITED);
                    assertThat(biz.details()).containsEntry("limit", 20).containsEntry("scope", "JD_AI");
                });
    }

    @Test
    void failsOpenWhenRedisUnavailable() {
        when(redis.execute(any(RedisScript.class), anyList(), any()))
                .thenThrow(new IllegalStateException("redis down"));

        assertThatCode(() -> guard.checkAndCount(USER_ID)).doesNotThrowAnyException();
    }

    @SuppressWarnings("unchecked")
    private void stubCounter(Long value) {
        when(redis.execute(any(RedisScript.class), anyList(), any())).thenReturn(value);
    }
}
