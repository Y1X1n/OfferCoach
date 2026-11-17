package com.offercoach.identity.security;

import com.offercoach.pay.config.PayProperties;
import com.offercoach.pay.internal.InternalTokens;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 内部接口令牌校验：仅放行携带正确 {@code X-OC-Internal-Token} 的 {@code /internal/**} 请求。
 *
 * <p>oc-app 的业务内部接口只对 oc-pay 服务开放；令牌为部署期共享密钥，常量时间比较，
 * 未配置时拒绝所有内部调用（fail-closed）。安全链对该路径 permitAll，鉴权完全由本过滤器承担。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 16)
public class InternalTokenFilter extends OncePerRequestFilter {

    private static final String INTERNAL_PREFIX = "/internal/";
    private static final String UNAUTHORIZED_BODY = "{\"code\":\"UNAUTHENTICATED\",\"message\":\"内部令牌无效\"}";

    private final PayProperties properties;

    public InternalTokenFilter(PayProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (uri == null || !uri.startsWith(INTERNAL_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }
        String provided = request.getHeader(properties.getInternalHeader());
        if (!InternalTokens.matches(properties.getInternalToken(), provided)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(UNAUTHORIZED_BODY);
            return;
        }
        filterChain.doFilter(request, response);
    }
}
