package com.offercoach.pay.service.web;

import com.offercoach.common.trace.TraceContext;
import com.offercoach.pay.config.PayProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 支付服务侧 trace 上下文：读取 oc-app 透传的 request/user 头写入 MDC，
 * 让订单、回调、对账日志能与上游请求串联。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceMdcFilter extends OncePerRequestFilter {

    private static final Pattern ID_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private final PayProperties properties;

    public TraceMdcFilter(PayProperties properties) {
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

        MDC.put(TraceContext.REQUEST_ID, requestId);
        MDC.put(TraceContext.TRACE_ID, requestId);
        if (userId != null) {
            MDC.put(TraceContext.USER_ID, userId);
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
