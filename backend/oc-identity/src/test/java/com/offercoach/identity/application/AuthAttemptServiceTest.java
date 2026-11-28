package com.offercoach.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.offercoach.common.error.BizException;
import com.offercoach.common.error.ErrorCode;
import com.offercoach.identity.application.settings.PlatformSettingsService;
import com.offercoach.identity.config.RateLimitProperties;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * 登录/注册限流与账号失败锁定。
 *
 * <p>这里的取舍都是安全语义：先占计数再校验密码（并发下不放大校验次数）、Redis 故障放行
 * （缓存挂了不能让人登不进来）、成功清零、失败不重复累加。断言都对着这几个不变量写。</p>
 */
class AuthAttemptServiceTest {

    private static final String IP = "203.0.113.9";
    private static final String ACCOUNT = "User@Example.com";

    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private RateLimitProperties properties;
    private PlatformSettingsService settingsService;
    private AuthAttemptService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        properties = new RateLimitProperties();
        settingsService = mock(PlatformSettingsService.class);
        when(settingsService.snapshot()).thenReturn(snapshot(5, 5));
        service = new AuthAttemptService(redis, properties, settingsService);
    }

    // ---- 登录预占 ----

    @Test
    void reservesAttemptAndKeepsCountingBelowThreshold() {
        stubCounter(1L);

        AuthAttemptService.LoginAttempt attempt = service.reserveLoginAttempt(ACCOUNT, IP);

        assertThat(attempt.counted()).isTrue();
        // 计数键按账号归一化（小写 + 摘要），不会把原始邮箱写进 Redis 键
        ArgumentCaptor<List<String>> keys = keysCaptor();
        verify(redis).execute(any(RedisScript.class), keys.capture(), eq(Long.toString(properties.getFailWindow().toMillis())));
        assertThat(keys.getValue().getFirst()).startsWith("oc:login:fail:acct:");
        assertThat(keys.getValue().getFirst()).doesNotContain("@");
    }

    @Test
    void locksAccountOnceThresholdExceeded() {
        stubCounter(properties.getFailThreshold() + 1L);

        assertThatThrownBy(() -> service.reserveLoginAttempt(ACCOUNT, IP))
                .isInstanceOf(BizException.class)
                .extracting(exception -> ((BizException) exception).errorCode())
                .isEqualTo(ErrorCode.RATE_LIMITED);
    }

    @Test
    void failsOpenWhenCounterUnavailable() {
        when(redis.execute(any(RedisScript.class), anyList(), any())).thenThrow(new IllegalStateException("redis down"));

        AuthAttemptService.LoginAttempt attempt = service.reserveLoginAttempt(ACCOUNT, IP);

        assertThat(attempt.counted())
                .as("Redis 故障时放行，但标记为未计数，失败路径会再补一次")
                .isFalse();
        assertThat(attempt.key()).isNull();
    }

    @Test
    void untrackedForBlankAccountWithoutTouchingRedis() {
        AuthAttemptService.LoginAttempt attempt = service.reserveLoginAttempt("   ", IP);

        assertThat(attempt.counted()).isFalse();
        // IP 窗口计数仍会跑一次，但账号计数（第二次 execute）不应发生
        verify(redis).execute(any(RedisScript.class), anyList(), any());
    }

    @Test
    void enforcesLoginPerMinuteWindow() {
        stubCounter(properties.getLoginPerMinute() + 1L);

        assertThatThrownBy(() -> service.reserveLoginAttempt(ACCOUNT, IP))
                .isInstanceOf(BizException.class)
                .extracting(exception -> ((BizException) exception).errorCode())
                .isEqualTo(ErrorCode.RATE_LIMITED);
    }

    // ---- 失败 / 成功 ----

    /** 预占时已计数，失败路径不能再加一次，否则 5 次失败阈值会被一半次数的尝试打满。 */
    @Test
    void failureDoesNotDoubleCountWhenReserved() {
        AuthAttemptService.LoginAttempt attempt = new AuthAttemptService.LoginAttempt("oc:login:fail:acct:x", true);

        service.onLoginFailure(attempt, ACCOUNT, IP);

        verifyNoInteractions(redis);
    }

    @Test
    void failureCountsWhenReservationWasNotTracked() {
        stubCounter(1L);

        service.onLoginFailure(new AuthAttemptService.LoginAttempt(null, false), ACCOUNT, IP);

        verify(redis).execute(any(RedisScript.class), anyList(), any());
    }

    @Test
    void failureSwallowsRedisFailure() {
        when(redis.execute(any(RedisScript.class), anyList(), any())).thenThrow(new IllegalStateException("redis down"));

        service.onLoginFailure(new AuthAttemptService.LoginAttempt(null, false), ACCOUNT, IP);
    }

    @Test
    void successClearsCounterEvenIfRedisFails() {
        when(redis.delete(any(String.class))).thenThrow(new IllegalStateException("redis down"));

        service.onLoginSuccess(new AuthAttemptService.LoginAttempt("oc:login:fail:acct:x", true), ACCOUNT);

        verify(redis).delete("oc:login:fail:acct:x");
    }

    // ---- 注册限流与日限 ----

    @Test
    void registrationPerMinuteWindowApplies() {
        stubCounter(properties.getRegisterPerMinute() + 1L);

        assertThatThrownBy(() -> service.checkRegisterAllowed(IP))
                .isInstanceOf(BizException.class)
                .extracting(exception -> ((BizException) exception).errorCode())
                .isEqualTo(ErrorCode.RATE_LIMITED);
    }

    @Test
    void registrationWindowUnlimitedWhenLimitDisabled() {
        properties.setRegisterPerMinute(0);

        service.checkRegisterAllowed(IP);

        verifyNoInteractions(redis);
    }

    @Test
    void rejectsWhenIpDailyCapReached() {
        when(settingsService.snapshot()).thenReturn(snapshot(2, 0));
        when(values.get(any(String.class))).thenReturn("2");

        assertThatThrownBy(() -> service.checkRegistrationDailyCaps(IP, "device-hash"))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).details())
                        .containsEntry("scope", "REGISTER_IP_DAILY"));
    }

    @Test
    void rejectsWhenDeviceDailyCapReached() {
        when(settingsService.snapshot()).thenReturn(snapshot(0, 3));
        when(values.get(any(String.class))).thenReturn("3");

        assertThatThrownBy(() -> service.checkRegistrationDailyCaps(IP, "device-hash"))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).details())
                        .containsEntry("scope", "REGISTER_DEVICE_DAILY"));
    }

    @Test
    void dailyCapsSkipWhenDisabled() {
        when(settingsService.snapshot()).thenReturn(snapshot(0, 0));

        service.checkRegistrationDailyCaps(IP, "device-hash");

        verify(redis, never()).opsForValue();
    }

    @Test
    void dailyCapsFailOpenOnRedisFailure() {
        when(settingsService.snapshot()).thenReturn(snapshot(3, 3));
        when(values.get(any(String.class))).thenThrow(new IllegalStateException("redis down"));

        service.checkRegistrationDailyCaps(IP, "device-hash");
    }

    @Test
    void malformedCounterIsTreatedAsUnlocked() {
        when(settingsService.snapshot()).thenReturn(snapshot(3, 3));
        when(values.get(any(String.class))).thenReturn("not-a-number");

        service.checkRegistrationDailyCaps(IP, "device-hash");
    }

    @Test
    void registrationSuccessSwallowsCounterFailure() {
        when(redis.execute(any(RedisScript.class), anyList(), any())).thenThrow(new IllegalStateException("redis down"));

        service.onRegistrationSuccess(IP, "device-hash");
    }

    @SuppressWarnings("unchecked")
    private void stubCounter(Long value) {
        when(redis.execute(any(RedisScript.class), anyList(), any())).thenReturn(value);
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<List<String>> keysCaptor() {
        return ArgumentCaptor.forClass(List.class);
    }

    private static PlatformSettingsService.Snapshot snapshot(int ipDailyCap, int deviceDailyCap) {
        return new PlatformSettingsService.Snapshot(
                true, 3, 5, 0, 0, 1, 1, 5, 20, ipDailyCap, deviceDailyCap, 10, 20, 50, 50, null, OffsetDateTime.now());
    }
}
