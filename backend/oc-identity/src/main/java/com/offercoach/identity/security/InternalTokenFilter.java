package com.offercoach.identity.security;

import com.offercoach.pay.config.PayProperties;
import com.offercoach.pay.internal.InternalTokens;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 内部接口令牌校验：仅放行携带正确 {@code X-OC-Internal-Token} 的 {@code /internal/**} 请求。
 *
 * <p>令牌为部署期共享密钥，常量时间比较，未配置时拒绝所有内部调用（fail-closed）。</p>
 *
 * <p>路径判定使用容器解码后的 {@code servletPath}，而不是原始 {@code requestURI}：后者保留百分号编码，
 * 攻击者可用 {@code /%69nternal/...} 这类写法绕过前缀判断，而 Spring MVC 仍会按解码后的路径命中内部接口。
 * 命中内部前缀但原始 URI 仍含编码或路径参数（{@code %}、{@code ;}）的请求一律拒绝，作为第二道拦截。</p>
 */
@Component
public class InternalTokenFilter extends OncePerRequestFilter {

    static final String INTERNAL_PREFIX = "/internal/";
    private static final String UNAUTHORIZED_BODY = "{\"code\":\"UNAUTHENTICATED\",\"message\":\"内部令牌无效\"}";
    private static final String ILLEGAL_PATH_BODY = "{\"code\":\"VALIDATION_FAILED\",\"message\":\"非法请求路径\"}";

    private final PayProperties properties;

    public InternalTokenFilter(PayProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = decodedPath(request);
        if (!path.startsWith(INTERNAL_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }
        String rawUri = request.getRequestURI();
        if (rawUri != null && (rawUri.indexOf('%') >= 0 || rawUri.indexOf(';') >= 0)) {
            writeJson(response, HttpServletResponse.SC_BAD_REQUEST, ILLEGAL_PATH_BODY);
            return;
        }
        String provided = request.getHeader(properties.getInternalHeader());
        if (!InternalTokens.matches(properties.getInternalToken(), provided)) {
            writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, UNAUTHORIZED_BODY);
            return;
        }
        filterChain.doFilter(request, response);
    }

    /**
     * 容器解码后的请求路径。
     *
     * <p>servletPath 通常已是解码结果；非根映射的 servlet 还需拼上 pathInfo。
     * 两者都为空时返回空串（视为非内部路径，交由安全链的 {@code denyAll} / 认证处理），
     * 避免退回原始 URI 而重新引入编码绕过。</p>
     */
    static String decodedPath(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        String pathInfo = request.getPathInfo();
        if (pathInfo != null && !pathInfo.isEmpty()) {
            return servletPath + pathInfo;
        }
        return servletPath == null ? "" : servletPath;
    }

    private static void writeJson(HttpServletResponse response, int status, String body) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(body);
    }
}
