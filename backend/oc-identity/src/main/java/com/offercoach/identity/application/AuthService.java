package com.offercoach.identity.application;

import com.offercoach.common.cache.RedisKeys;
import com.offercoach.common.error.BizException;
import com.offercoach.common.error.ErrorCode;
import com.offercoach.identity.application.captcha.CaptchaService;
import com.offercoach.identity.application.invite.InviteService;
import com.offercoach.identity.application.port.UserRepository;
import com.offercoach.identity.application.quota.QuotaService;
import com.offercoach.identity.application.quota.QuotaSnapshot;
import com.offercoach.identity.application.settings.PlatformSettingsService;
import com.offercoach.identity.config.AuthProperties;
import com.offercoach.identity.domain.account.PasswordPolicy;
import com.offercoach.identity.domain.user.NewUser;
import com.offercoach.identity.domain.user.UserAccount;
import com.offercoach.identity.security.JwtService;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 注册、登录、刷新、登出用例编排。
 *
 * <p>只返回应用层会话对象，不向 API 层暴露 DO。</p>
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String ROLE_USER = "USER";
    private static final String PLAN_FREE = "FREE";
    private static final String DEFAULT_TIMEZONE = "Asia/Shanghai";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final AuthAttemptService authAttemptService;
    private final AuthProperties properties;
    private final StringRedisTemplate redisTemplate;
    private final QuotaService quotaService;
    private final PlatformSettingsService settingsService;
    private final CaptchaService captchaService;
    private final InviteService inviteService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            AuthAttemptService authAttemptService,
            AuthProperties properties,
            StringRedisTemplate redisTemplate,
            QuotaService quotaService,
            PlatformSettingsService settingsService,
            CaptchaService captchaService,
            InviteService inviteService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.authAttemptService = authAttemptService;
        this.properties = properties;
        this.redisTemplate = redisTemplate;
        this.quotaService = quotaService;
        this.settingsService = settingsService;
        this.captchaService = captchaService;
        this.inviteService = inviteService;
    }

    /**
     * 注册：注册开关由运行时策略控制；验证码一次性校验；邀请码可选。
     */
    @Transactional
    public AuthSession register(RegisterCommand command) {
        authAttemptService.checkRegisterAllowed(command.ip());
        String normalizedEmail = normalizeEmail(command.email());
        boolean opsBypass = requireConfiguredCode(command.verifyCode());
        if (!opsBypass && !settingsService.snapshot().registrationOpen()) {
            throw fieldError("verifyCode", "注册暂未开放，请稍后再试");
        }
        if (!opsBypass) {
            captchaService.verify(command.captchaId(), command.captchaAnswer());
        }
        authAttemptService.checkRegistrationDailyCaps(command.ip(), command.deviceHash());
        String trimmedNickname = validateNickname(command.nickname());
        validatePassword(command.password());

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw duplicateEmail();
        }

        NewUser newUser = new NewUser(
                normalizedEmail,
                passwordEncoder.encode(command.password()),
                trimmedNickname,
                ROLE_USER,
                STATUS_ACTIVE,
                DEFAULT_TIMEZONE);
        UserAccount user;
        try {
            user = userRepository.insert(newUser);
        } catch (DuplicateKeyException ex) {
            throw duplicateEmail();
        }

        quotaService.initializeQuota(user.id(), PLAN_FREE);
        inviteService.onRegistered(user.id(), command.inviteCode(), command.ip(), command.deviceHash());
        authAttemptService.onRegistrationSuccess(command.ip(), command.deviceHash());
        return issueSession(user, PLAN_FREE);
    }

    /**
     * 注册页策略：是否开放、是否需要固定验证码/算术验证码、邀请奖励说明。
     */
    public RegistrationPolicy registrationPolicy() {
        AuthProperties.DevVerifyCode dev = properties.getDevVerifyCode();
        AuthProperties.InviteCode ops = properties.getInviteCode();
        boolean requireVerifyCode = dev.isEnabled() || ops.isEnabled();
        PlatformSettingsService.Snapshot settings = settingsService.snapshot();
        boolean open = requireVerifyCode || settings.registrationOpen();
        return new RegistrationPolicy(
                open,
                open && !requireVerifyCode,
                requireVerifyCode,
                true,
                settings.inviteRewardInviterInterview(),
                settings.inviteRewardInviteeResume(),
                settings.dailyInterviewLimit(),
                settings.dailyResumeLimit());
    }

    @Transactional
    public AuthSession login(String account, String password, String ip) {
        if (account == null || account.isBlank() || password == null || password.isEmpty()) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        AuthAttemptService.LoginAttempt attempt = authAttemptService.reserveLoginAttempt(account, ip);
        UserAccount user = findByEmail(account).filter(UserAccount::isActive).orElse(null);
        if (user == null || !passwordMatches(password, user)) {
            authAttemptService.onLoginFailure(attempt, account, ip);
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        authAttemptService.onLoginSuccess(attempt, account);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        userRepository.updateLastLogin(user.id(), now);
        return issueSession(user, planCodeOf(user.id()));
    }

    /**
     * 轮换 Refresh Token：消费旧 Cookie 后在同一家族内重新签发 Access + 新 Refresh。
     */
    public AuthSession refresh(String refreshToken) {
        RefreshTokenService.RefreshSession consumed = refreshTokenService.rotate(refreshToken);
        UserAccount user = userRepository
                .findById(consumed.userId())
                .filter(UserAccount::isActive)
                .orElseThrow(() -> new BizException(ErrorCode.REFRESH_INVALID));
        return issueSession(user, planCodeOf(user.id()), consumed.familyId());
    }

    /**
     * 登出：Access jti 写黑名单、吊销 Refresh 家族；任一失败即抛出，避免返回 204 但吊销未生效。
     */
    public void logout(Jwt accessToken, String refreshToken) {
        blacklistAccessToken(accessToken);
        refreshTokenService.revokeFamily(refreshToken);
    }

    private AuthSession issueSession(UserAccount user, String planCode) {
        return issueSession(user, planCode, null);
    }

    private AuthSession issueSession(UserAccount user, String planCode, String familyId) {
        JwtService.IssuedAccessToken accessToken = jwtService.issue(user.id(), user.role());
        RefreshTokenService.IssuedRefresh issued = familyId == null
                ? refreshTokenService.issueNew(user.id())
                : refreshTokenService.issueInFamily(user.id(), familyId);
        return new AuthSession(
                user.id(),
                user.nickname(),
                user.role(),
                planCode,
                accessToken.token(),
                accessToken.expiresIn(),
                issued.token());
    }

    private void blacklistAccessToken(Jwt accessToken) {
        if (accessToken == null) {
            return;
        }
        String jwtId = accessToken.getId();
        Instant expiresAt = accessToken.getExpiresAt();
        if (jwtId == null || jwtId.isBlank() || expiresAt == null) {
            return;
        }
        Duration ttl = Duration.between(Instant.now(), expiresAt);
        if (ttl.isNegative() || ttl.isZero()) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(RedisKeys.accessBlacklist(jwtId), "1", ttl);
        } catch (RuntimeException ex) {
            log.warn("Access Token 黑名单写入失败，登出按失败处理：{}", ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private Optional<UserAccount> findByEmail(String email) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        return userRepository.findByEmail(normalized);
    }

    private boolean passwordMatches(String rawPassword, UserAccount user) {
        String passwordHash = user.passwordHash();
        if (passwordHash == null || passwordHash.isBlank()) {
            return false;
        }
        return passwordEncoder.matches(rawPassword, passwordHash);
    }

    private String planCodeOf(long userId) {
        try {
            QuotaSnapshot snapshot = quotaService.snapshot(userId);
            if (snapshot != null
                    && snapshot.planCode() != null
                    && !snapshot.planCode().isBlank()) {
                return snapshot.planCode();
            }
        } catch (RuntimeException ex) {
            log.warn("用户 {} 套餐查询失败，回退 FREE：{}", userId, ex.getClass().getSimpleName());
        }
        return PLAN_FREE;
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw fieldError("email", "邮箱不能为空");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private void validatePassword(String password) {
        if (!PasswordPolicy.isValid(password)) {
            throw fieldError("password", "密码需为 8-64 位且同时包含字母和数字");
        }
    }

    private String validateNickname(String nickname) {
        String trimmed = nickname == null ? null : nickname.trim();
        if (trimmed == null || trimmed.length() < 2 || trimmed.length() > 40) {
            throw fieldError("nickname", "昵称长度需为 2-40");
        }
        return trimmed;
    }

    /**
     * 校验运维配置码（开发验证码 / 固定邀请码）；命中时返回 true，可绕过注册开关与算术验证码。
     */
    private boolean requireConfiguredCode(String verifyCode) {
        AuthProperties.DevVerifyCode devVerifyCode = properties.getDevVerifyCode();
        if (devVerifyCode.isEnabled()) {
            if (!matches(devVerifyCode.getCode(), verifyCode)) {
                throw fieldError("verifyCode", "验证码不正确");
            }
            return true;
        }
        AuthProperties.InviteCode inviteCode = properties.getInviteCode();
        if (inviteCode.isEnabled()) {
            if (!matches(inviteCode.getCode(), verifyCode)) {
                throw fieldError("verifyCode", "邀请码不正确");
            }
            return true;
        }
        return false;
    }

    private static boolean matches(String expected, String provided) {
        return expected != null && !expected.isBlank() && provided != null && expected.equals(provided.trim());
    }

    private BizException duplicateEmail() {
        return fieldError("email", "该邮箱已注册");
    }

    private static BizException fieldError(String field, String message) {
        return new BizException(ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.<String, Object>of(field, message)));
    }

    /**
     * 应用层会话结果；由 Controller 转换为响应 DTO。
     */
    public record AuthSession(
            long userId,
            String nickname,
            String role,
            String planCode,
            String accessToken,
            long expiresIn,
            String refreshToken) {}

    /**
     * 注册命令：运维验证码与可选邀请码分开，验证码只用于人机校验。
     */
    public record RegisterCommand(
            String email,
            String password,
            String nickname,
            String verifyCode,
            String inviteCode,
            String captchaId,
            String captchaAnswer,
            String ip,
            String deviceHash) {}

    public record RegistrationPolicy(
            boolean registrationOpen,
            boolean requireCaptcha,
            boolean requireVerifyCode,
            boolean inviteOptional,
            int inviteRewardInviterInterview,
            int inviteRewardInviteeResume,
            int dailyInterviewLimit,
            int dailyResumeLimit) {}
}
