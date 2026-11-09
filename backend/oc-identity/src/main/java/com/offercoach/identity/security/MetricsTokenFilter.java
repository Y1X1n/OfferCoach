package com.offercoach.identity.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 可选 Prometheus 抓取鉴权：配置 oc.metrics-token 后要求 Authorization: Bearer <token>。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 15)
public class MetricsTokenFilter extends OncePerRequestFilter {

    private static final String METRICS_PATH = "/actuator/prometheus";

    private final String metricsToken;

    public MetricsTokenFilter(@Value("${oc.metrics-token:}") String metricsToken) {
        this.metricsToken = metricsToken == null ? "" : metricsToken.trim();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (metricsToken.isBlank() || !METRICS_PATH.equals(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }
        String authorization = request.getHeader("Authorization");
        String provided = authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring("Bearer ".length()).trim()
                : "";
        if (!MessageDigest.isEqual(
                metricsToken.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8))) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        filterChain.doFilter(request, response);
    }
}
