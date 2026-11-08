package com.offercoach.identity.application;

import com.offercoach.identity.config.AuthProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Optional;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Refresh Token Cookie 的构建、读取与清除，供认证 Controller 复用。
 */
@Component
public class RefreshCookieFactory {

    private final AuthProperties properties;

    public RefreshCookieFactory(AuthProperties properties) {
        this.properties = properties;
    }

    public ResponseCookie create(String refreshToken) {
        return base(refreshToken).maxAge(properties.getRefreshTokenTtl()).build();
    }

    public ResponseCookie clear() {
        return base("").maxAge(Duration.ZERO).build();
    }

    public Optional<String> read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        String cookieName = properties.getCookie().getName();
        for (Cookie cookie : cookies) {
            String value = cookie.getValue();
            if (cookieName.equals(cookie.getName()) && value != null && !value.isBlank()) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        AuthProperties.Cookie cookie = properties.getCookie();
        return ResponseCookie.from(cookie.getName(), value)
                .httpOnly(true)
                .secure(cookie.isSecure())
                .sameSite(cookie.getSameSite())
                .path(cookie.getPath());
    }
}
