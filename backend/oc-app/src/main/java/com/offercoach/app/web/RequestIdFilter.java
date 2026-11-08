package com.offercoach.app.web;

import com.offercoach.common.trace.TraceContext;
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

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    private static final Pattern REQUEST_ID_PATTERN = Pattern.compile("[A-Za-z0-9_-]{8,64}");
    private static final Pattern TRACEPARENT_PATTERN =
            Pattern.compile("^00-([0-9a-f]{32})-([0-9a-f]{16})-[0-9a-f]{2}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = request.getHeader("X-Request-Id");
        if (requestId == null || !REQUEST_ID_PATTERN.matcher(requestId).matches()) {
            requestId = "req_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
        String traceId = extractTraceId(request.getHeader("traceparent"));

        MDC.put(TraceContext.REQUEST_ID, requestId);
        MDC.put(TraceContext.TRACE_ID, traceId);
        response.setHeader("X-Request-Id", requestId);
        response.setHeader("X-Trace-Id", traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(TraceContext.REQUEST_ID);
            MDC.remove(TraceContext.TRACE_ID);
            MDC.remove(TraceContext.USER_ID);
        }
    }

    private String extractTraceId(String traceparent) {
        if (traceparent != null) {
            var matcher = TRACEPARENT_PATTERN.matcher(traceparent);
            if (matcher.matches()) {
                return matcher.group(1);
            }
        }
        return UUID.randomUUID().toString().replace("-", "");
    }
}
