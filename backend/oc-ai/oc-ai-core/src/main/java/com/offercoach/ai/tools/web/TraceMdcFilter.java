package com.offercoach.ai.tools.web;

import com.offercoach.ai.tools.config.ToolsProperties;
import com.offercoach.common.trace.TraceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 工具服务侧 trace 上下文：读取 oc-app 透传的 request/user/session 头写入 MDC，
 * 让 ASR/TTS/OCR/Search/Fetch 的日志能与上游请求串联。
 *
 * <p>只在工具服务开启（{@code oc.ai.tools.trace-mdc=true}），避免 oc-app 内重复注册。</p>
 */
@Component
@ConditionalOnProperty(name = "oc.ai.tools.trace-mdc", havingValue = "true")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceMdcFilter extends OncePerRequestFilter {

    private static final Pattern ID_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private final ToolsProperties properties;

    public TraceMdcFilter(ToolsProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = valid(request.getHeader(properties.getRequestIdHeader()));
        if (requestId == null) {
            requestId = "req_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
        String userId = valid(request.getHeader(properties.getUserIdHeader()));
        String sessionId = valid(request.getHeader(properties.getSessionIdHeader()));

        MDC.put(TraceContext.REQUEST_ID, requestId);
        MDC.put(TraceContext.TRACE_ID, requestId);
        if (userId != null) {
            MDC.put(TraceContext.USER_ID, userId);
        }
        if (sessionId != null) {
            MDC.put(TraceContext.SESSION_ID, sessionId);
        }
        response.setHeader(properties.getRequestIdHeader(), requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            TraceContext.clear();
        }
    }

    private static String valid(String value) {
        return value != null && ID_PATTERN.matcher(value).matches() ? value : null;
    }
}
