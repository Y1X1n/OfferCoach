package com.offercoach.identity.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offercoach.common.error.ApiError;
import com.offercoach.common.error.ErrorCode;
import com.offercoach.common.trace.TraceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * 未认证统一输出 ApiError：仅 JWT 时间校验明确过期时返回 TOKEN_EXPIRED，
 * 已吊销（revoked）与状态不可用（server_error）等一律返回 UNAUTHENTICATED。
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        ErrorCode errorCode = isExpired(authException) ? ErrorCode.TOKEN_EXPIRED : ErrorCode.UNAUTHENTICATED;
        write(response, errorCode);
    }

    private void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.httpStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ApiError error = ApiError.of(errorCode, null, Map.of(), TraceContext.requestId(), TraceContext.traceId());
        objectMapper.writeValue(response.getWriter(), error);
    }

    private boolean isExpired(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof JwtValidationException validationException) {
                return validationException.getErrors().stream().anyMatch(this::isExpired);
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean isExpired(OAuth2Error error) {
        String description = error.getDescription();
        return description != null && description.toLowerCase(Locale.ROOT).contains("expired");
    }
}
