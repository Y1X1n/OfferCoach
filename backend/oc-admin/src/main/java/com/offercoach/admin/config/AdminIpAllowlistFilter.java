package com.offercoach.admin.config;

import com.offercoach.identity.security.ClientIpResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 可选管理端 IP 白名单；配置 oc.admin.allowed-ips 后仅允许列表内客户端访问 /api/v1/admin/**。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class AdminIpAllowlistFilter extends OncePerRequestFilter {

    private static final String ADMIN_PREFIX = "/api/v1/admin";

    private final AdminProperties properties;
    private final ClientIpResolver clientIpResolver;

    public AdminIpAllowlistFilter(AdminProperties properties, ClientIpResolver clientIpResolver) {
        this.properties = properties;
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        List<String> allowed = properties.getAllowedIps();
        String path = request.getRequestURI();
        if (allowed.isEmpty() || !path.startsWith(ADMIN_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }
        String clientIp = clientIpResolver.resolve(request);
        if (allowed.stream().anyMatch(ip -> ip != null && ip.trim().equals(clientIp))) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"code\":\"FORBIDDEN\",\"message\":\"管理端访问受限\"}");
    }
}
