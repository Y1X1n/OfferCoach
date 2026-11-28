package com.offercoach.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.offercoach.common.error.BizException;
import com.offercoach.common.error.ErrorCode;
import com.offercoach.identity.application.captcha.CaptchaService;
import com.offercoach.identity.application.invite.InviteService;
import com.offercoach.identity.application.port.UserRepository;
import com.offercoach.identity.application.quota.QuotaService;
import com.offercoach.identity.application.quota.QuotaSnapshot;
import com.offercoach.identity.application.settings.PlatformSettingsService;
import com.offercoach.identity.config.AuthProperties;
import com.offercoach.identity.domain.user.UserAccount;
import com.offercoach.identity.security.JwtService;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * 登录/刷新/登出的安全语义。
 *
 * <p>重点不在"能不能登录"，而在几条容易被改坏的边界：账号不存在时仍要做一次密码比对
 * （否则响应时间能枚举账号）、禁用账号与不存在账号给出同一个错误、Redis 写黑名单失败
 * 必须让登出失败（不能返回 204 却吊销未生效）。</p>
 */
class AuthServiceTest {

    private static final String EMAIL = "user@example.com";
    private static final String DUMMY_HASH = "dummy-hash";

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private RefreshTokenService refreshTokenService;
    private AuthAttemptService authAttemptService;
    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private QuotaService quotaService;
    private PlatformSettingsService settingsService;
    private InviteService inviteService;
    private AuthService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtService = mock(JwtService.class);
        refreshTokenService = mock(RefreshTokenService.class);
        authAttemptService = mock(AuthAttemptService.class);
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        quotaService = mock(QuotaService.class);
        settingsService = mock(PlatformSettingsService.class);
        inviteService = mock(InviteService.class);

        when(passwordEncoder.encode(anyString())).thenReturn(DUMMY_HASH);
        when(authAttemptService.reserveLoginAttempt(anyString(), anyString()))
                .thenReturn(new AuthAttemptService.LoginAttempt("oc:login:fail:acct:x", true));
        when(jwtService.issue(anyLong(), anyString())).thenReturn(new JwtService.IssuedAccessToken("access", 900));
        when(refreshTokenService.issueNew(anyLong()))
                .thenReturn(new RefreshTokenService.IssuedRefresh("refresh", 7L, "family-1", "jti-1"));
        when(refreshTokenService.issueInFamily(anyLong(), anyString()))
                .thenReturn(new RefreshTokenService.IssuedRefresh("refresh2", 7L, "family-1", "jti-2"));
        when(quotaService.snapshot(anyLong()))
                .thenReturn(new QuotaSnapshot("PRO", "专业版", 1, 1, null, null, 0, 0, Map.of()));

        service = new AuthService(
                userRepository,
                passwordEncoder,
                jwtService,
                refreshTokenService,
                authAttemptService,
                new AuthProperties(),
                redis,
                quotaService,
                settingsService,
                mock(CaptchaService.class),
                inviteService);
    }

    // ---- 登录 ----

    /**
     * 账号不存在时也必须走一次密码比对（对着内部 dummy hash）：直接短路会让"账号不存在"
     * 明显快于"密码错误"，等于对外提供账号枚举接口。
     */
    @Test
    void unknownAccountStillRunsPasswordCheckThenFails() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(passwordEncoder.matches("whatever", DUMMY_HASH)).thenReturn(false);

        assertThatThrownBy(() -> service.login(EMAIL, "whatever", "203.0.113.9"))
                .isInstanceOf(BizException.class)
                .extracting(exception -> ((BizException) exception).errorCode())
                .isEqualTo(ErrorCode.UNAUTHENTICATED);

        verify(passwordEncoder).matches("whatever", DUMMY_HASH);
        verify(authAttemptService).onLoginFailure(any(), eq(EMAIL), anyString());
        verify(jwtService, never()).issue(anyLong(), anyString());
    }

    /** 禁用账号与不存在账号返回同一个错误，否则可以借错误码判断账号是否存在。 */
    @Test
    void disabledAccountIsIndistinguishableFromUnknownAccount() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user("DISABLED")));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> service.login(EMAIL, "correct-password", "203.0.113.9"))
                .isInstanceOf(BizException.class)
                .extracting(exception -> ((BizException) exception).errorCode())
                .isEqualTo(ErrorCode.UNAUTHENTICATED);

        // 禁用账号同样要对着 dummy hash 比一次，否则"被禁用"会比"不存在"更慢
        verify(passwordEncoder).matches("correct-password", DUMMY_HASH);
        verify(authAttemptService).onLoginFailure(any(), eq(EMAIL), anyString());
        verify(userRepository, never()).updateLastLogin(anyLong(), any());
    }

    @Test
    void wrongPasswordCountsFailureAndIssuesNothing() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user("ACTIVE")));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> service.login(EMAIL, "wrong-password", "203.0.113.9"))
                .isInstanceOf(BizException.class);

        verify(authAttemptService).onLoginFailure(any(), eq(EMAIL), anyString());
        verify(authAttemptService, never()).onLoginSuccess(any(), anyString());
        verify(refreshTokenService, never()).issueNew(anyLong());
    }

    @Test
    void successClearsCounterUpdatesLastLoginAndUsesPlanCode() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user("ACTIVE")));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        AuthService.AuthSession session = service.login(EMAIL, "correct-password", "203.0.113.9");

        assertThat(session.accessToken()).isEqualTo("access");
        assertThat(session.refreshToken()).isEqualTo("refresh");
        assertThat(session.planCode()).isEqualTo("PRO");
        verify(authAttemptService).onLoginSuccess(any(), eq(EMAIL));
        verify(userRepository).updateLastLogin(eq(7L), any(OffsetDateTime.class));
    }

    @Test
    void quotaLookupFailureFallsBackToFreePlan() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user("ACTIVE")));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(quotaService.snapshot(anyLong())).thenThrow(new IllegalStateException("db down"));

        assertThat(service.login(EMAIL, "correct-password", "203.0.113.9").planCode())
                .isEqualTo("FREE");
    }

    @Test
    void blankCredentialsAreRejectedBeforeTouchingRedis() {
        assertThatThrownBy(() -> service.login(" ", "password", "203.0.113.9"))
                .isInstanceOf(BizException.class)
                .extracting(exception -> ((BizException) exception).errorCode())
                .isEqualTo(ErrorCode.UNAUTHENTICATED);

        verifyNoInteractions(authAttemptService);
    }

    // ---- 刷新 ----

    /** 用户被禁用后旧 Refresh 立刻失效，不能靠它换出新 Access。 */
    @Test
    void refreshRejectsDeactivatedUser() {
        when(refreshTokenService.rotate("old"))
                .thenReturn(new RefreshTokenService.RefreshSession(7L, "family-1", "jti-old"));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user("DISABLED")));

        assertThatThrownBy(() -> service.refresh("old"))
                .isInstanceOf(BizException.class)
                .extracting(exception -> ((BizException) exception).errorCode())
                .isEqualTo(ErrorCode.REFRESH_INVALID);

        verify(jwtService, never()).issue(anyLong(), anyString());
    }

    @Test
    void refreshContinuesSameFamily() {
        when(refreshTokenService.rotate("old"))
                .thenReturn(new RefreshTokenService.RefreshSession(7L, "family-1", "jti-old"));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user("ACTIVE")));

        service.refresh("old");

        verify(refreshTokenService).issueInFamily(7L, "family-1");
        verify(refreshTokenService, never()).issueNew(anyLong());
    }

    // ---- 登出 ----

    @Test
    void logoutWithoutAccessTokenStillRevokesFamily() {
        service.logout(null, "refresh");

        verify(refreshTokenService).revokeFamily("refresh");
        verifyNoInteractions(redis);
    }

    /** 已过期的 Access 不需要（也无法）写黑名单；但登出不能因此报错。 */
    @Test
    void expiredAccessTokenIsNotBlacklisted() {
        service.logout(jwt(Instant.now().minusSeconds(60)), "refresh");

        verifyNoInteractions(values);
        verify(refreshTokenService).revokeFamily("refresh");
    }

    @Test
    void logoutBlacklistsAccessTokenForRemainingLifetime() {
        Instant expiry = Instant.now().plusSeconds(600);

        service.logout(jwt(expiry), "refresh");

        ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
        verify(values).set(anyString(), eq("1"), ttl.capture());
        assertThat(ttl.getValue()).isBetween(Duration.ofSeconds(500), Duration.ofSeconds(601));
        verify(refreshTokenService).revokeFamily("refresh");
    }

    /** 黑名单写不进去就等同于"Access 仍然有效"，必须让登出失败而不是返回成功。 */
    @Test
    void logoutFailsWhenBlacklistCannotBeWritten() {
        // ValueOperations.set 返回 void，只能用 doThrow 打桩
        doThrow(new IllegalStateException("redis down"))
                .when(values)
                .set(anyString(), anyString(), any(Duration.class));

        assertThatThrownBy(() -> service.logout(jwt(Instant.now().plusSeconds(600)), "refresh"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void jwtWithoutIdIsSkipped() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getId()).thenReturn(" ");

        service.logout(jwt, "refresh");

        verifyNoInteractions(values);
        verify(refreshTokenService).revokeFamily("refresh");
    }

    private static UserAccount user(String status) {
        return new UserAccount(
                7L,
                EMAIL,
                "stored-hash",
                "昵称",
                null,
                "USER",
                status,
                "Asia/Shanghai",
                OffsetDateTime.now(),
                null,
                null);
    }

    private static Jwt jwt(Instant expiresAt) {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getId()).thenReturn("jti-access");
        when(jwt.getExpiresAt()).thenReturn(expiresAt);
        return jwt;
    }
}
