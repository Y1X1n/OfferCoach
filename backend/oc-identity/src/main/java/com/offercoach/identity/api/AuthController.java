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

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String X_REAL_IP = "X-Real-IP";
    private static final String UNKNOWN_IP = "unknown";

    private final AuthService authService;
    private final RefreshCookieFactory refreshCookieFactory;
    private final CurrentUser currentUser;
    private final CaptchaService captchaService;
    private final DeviceFingerprint deviceFingerprint;

    public AuthController(
            AuthService authService,
            RefreshCookieFactory refreshCookieFactory,
            CurrentUser currentUser,
            CaptchaService captchaService,
            DeviceFingerprint deviceFingerprint) {
        this.authService = authService;
        this.refreshCookieFactory = refreshCookieFactory;
        this.currentUser = currentUser;
        this.captchaService = captchaService;
        this.deviceFingerprint = deviceFingerprint;
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
        CaptchaService.Challenge challenge = captchaService.issue(clientIp(request));
        return new CaptchaResponse(challenge.captchaId(), challenge.question(), challenge.expiresIn());
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        String ip = clientIp(httpRequest);
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
                authService.login(request.account(), request.password(), clientIp(httpRequest));
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

    private String clientIp(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        if (remoteAddress == null || remoteAddress.isBlank()) {
            return UNKNOWN_IP;
        }
        if (!isPrivateAddress(remoteAddress)) {
            return remoteAddress;
        }
        // Nginx 覆写 X-Real-IP；不要信任客户端可伪造的 X-Forwarded-For 首段。
        String realIp = request.getHeader(X_REAL_IP);
        if (realIp != null && isIpLiteral(realIp.trim())) {
            return realIp.trim();
        }
        String forwardedFor = request.getHeader(X_FORWARDED_FOR);
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return remoteAddress;
        }
        String first = forwardedFor.split(",")[0].trim();
        return isIpLiteral(first) ? first : remoteAddress;
    }

    private static boolean isPrivateAddress(String address) {
        if (address.startsWith("::ffff:")) {
            return isPrivateAddress(address.substring("::ffff:".length()));
        }
        if ("::1".equals(address) || "0:0:0:0:0:0:0:1".equals(address)) {
            return true;
        }
        if (address.startsWith("127.")
                || address.startsWith("10.")
                || address.startsWith("192.168.")
                || address.startsWith("169.254.")
                || address.startsWith("fe80:")
                || address.startsWith("fc")
                || address.startsWith("fd")) {
            return true;
        }
        if (address.startsWith("172.")) {
            String[] parts = address.split("\\.");
            if (parts.length < 2) {
                return false;
            }
            try {
                int second = Integer.parseInt(parts[1]);
                return second >= 16 && second <= 31;
            } catch (NumberFormatException ex) {
                return false;
            }
        }
        return false;
    }

    private static boolean isIpLiteral(String value) {
        if (value.isEmpty() || value.length() > 45) {
            return false;
        }
        if (value.indexOf('.') < 0 && value.indexOf(':') < 0) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            boolean allowed = (ch >= '0' && ch <= '9')
                    || (ch >= 'a' && ch <= 'f')
                    || (ch >= 'A' && ch <= 'F')
                    || ch == ':'
                    || ch == '.';
            if (!allowed) {
                return false;
            }
        }
        return true;
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
