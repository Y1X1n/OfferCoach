package com.offercoach.identity.api;

import com.offercoach.identity.api.dto.AuthResponse;
import com.offercoach.identity.api.dto.CaptchaResponse;
import com.offercoach.identity.api.dto.LoginRequest;
import com.offercoach.identity.api.dto.RegisterRequest;
import com.offercoach.identity.api.dto.RegistrationPolicyResponse;
import com.offercoach.identity.api.dto.UserSummary;
import com.offercoach.identity.application.AuthService;
import com.offercoach.identity.application.RefreshCookieFactory;
import com.offercoach.identity.application.captcha.CaptchaService;
import com.offercoach.identity.security.ClientIpResolver;
import com.offercoach.identity.security.CurrentUser;
import com.offercoach.identity.security.DeviceFingerprint;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证端点：注册、登录、刷新、登出。
 *
 * <p>注册/登录按客户端 IP 限流；仅当直连地址为回环/私有地址（受信代理）时才采信
 * {@code X-Forwarded-For} 的首个地址，避免客户端伪造。</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshCookieFactory refreshCookieFactory;
    private final CurrentUser currentUser;
    private final CaptchaService captchaService;
    private final DeviceFingerprint deviceFingerprint;
    private final ClientIpResolver clientIpResolver;

    public AuthController(
            AuthService authService,
            RefreshCookieFactory refreshCookieFactory,
            CurrentUser currentUser,
            CaptchaService captchaService,
            DeviceFingerprint deviceFingerprint,
            ClientIpResolver clientIpResolver) {
        this.authService = authService;
        this.refreshCookieFactory = refreshCookieFactory;
        this.currentUser = currentUser;
        this.captchaService = captchaService;
        this.deviceFingerprint = deviceFingerprint;
        this.clientIpResolver = clientIpResolver;
    }

    /**
     * 注册页策略：前端据此展示固定验证码、算术验证码或邀请码入口。
     */
    @GetMapping("/registration-policy")
    public RegistrationPolicyResponse registrationPolicy() {
        AuthService.RegistrationPolicy policy = authService.registrationPolicy();
        return new RegistrationPolicyResponse(
                policy.registrationOpen(),
                policy.requireCaptcha(),
                policy.requireVerifyCode(),
                policy.inviteOptional(),
                policy.inviteRewardInviterInterview(),
                policy.inviteRewardInviteeResume(),
                policy.dailyInterviewLimit(),
                policy.dailyResumeLimit());
    }

    /**
     * 签发一次性算术验证码；答案只存服务端，5 分钟内有效。
     */
    @PostMapping("/captcha")
    public CaptchaResponse captcha(HttpServletRequest request) {
        CaptchaService.Challenge challenge = captchaService.issue(clientIpResolver.resolve(request));
        return new CaptchaResponse(challenge.captchaId(), challenge.question(), challenge.expiresIn());
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        String ip = clientIpResolver.resolve(httpRequest);
        AuthService.AuthSession session = authService.register(new AuthService.RegisterCommand(
                request.email(),
                request.password(),
                request.nickname(),
                request.verifyCode(),
                request.inviteCode(),
                request.captchaId(),
                request.captchaAnswer(),
                ip,
                deviceFingerprint.hash(httpRequest)));
        return withRefreshCookie(HttpStatus.CREATED, session);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        AuthService.AuthSession session =
                authService.login(request.account(), request.password(), clientIpResolver.resolve(httpRequest));
        return withRefreshCookie(HttpStatus.OK, session);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(HttpServletRequest request) {
        String refreshToken = refreshCookieFactory.read(request).orElse(null);
        AuthService.AuthSession session = authService.refresh(refreshToken);
        return withRefreshCookie(HttpStatus.OK, session);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        authService.logout(currentUser.jwt(), refreshCookieFactory.read(request).orElse(null));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookieFactory.clear().toString())
                .build();
    }

    private ResponseEntity<AuthResponse> withRefreshCookie(HttpStatus status, AuthService.AuthSession session) {
        return ResponseEntity.status(status)
                .header(
                        HttpHeaders.SET_COOKIE,
                        refreshCookieFactory.create(session.refreshToken()).toString())
                .body(toResponse(session));
    }

    private AuthResponse toResponse(AuthService.AuthSession session) {
        UserSummary user = new UserSummary(
                Long.toString(session.userId()), session.nickname(), session.role(), session.planCode());
        return new AuthResponse(Long.toString(session.userId()), session.accessToken(), session.expiresIn(), user);
    }
}
