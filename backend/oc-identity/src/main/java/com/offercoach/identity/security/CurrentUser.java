package com.offercoach.identity.security;

import com.offercoach.common.error.BizException;
import com.offercoach.common.error.ErrorCode;
import java.time.Instant;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * 从 SecurityContext 读取当前登录用户。
 */
@Component
public class CurrentUser {

    private static final String ROLE_CLAIM = "role";

    public long userId() {
        String subject = jwt().getSubject();
        try {
            return Long.parseLong(subject);
        } catch (NumberFormatException ex) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
    }

    public String role() {
        return jwt().getClaimAsString(ROLE_CLAIM);
    }

    public Jwt jwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token) {
            return token.getToken();
        }
        if (authentication != null && authentication.getPrincipal() instanceof Jwt token) {
            return token;
        }
        throw new BizException(ErrorCode.UNAUTHENTICATED);
    }

    public String jwtId() {
        return jwt().getId();
    }

    public Instant expiresAt() {
        return jwt().getExpiresAt();
    }
}
