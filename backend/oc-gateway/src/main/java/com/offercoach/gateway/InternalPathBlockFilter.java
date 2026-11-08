package com.offercoach.gateway;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 拒绝外部访问 /internal/** 服务间接口，直接返回 403，不转发到后端。
 *
 * <p>使用 WebFilter 而非 GlobalFilter，确保请求在路由匹配之前被拦截。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InternalPathBlockFilter implements WebFilter {

    private static final String INTERNAL_PREFIX = "/internal/";
    private static final byte[] FALLBACK_BODY =
            "{\"code\":\"FORBIDDEN\",\"message\":\"内网接口不对外开放\",\"details\":{}}".getBytes(StandardCharsets.UTF_8);

    private final ObjectMapper objectMapper;

    public InternalPathBlockFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (!path.startsWith(INTERNAL_PREFIX)) {
            return chain.filter(exchange);
        }
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String requestId = requestId(exchange);
        String traceId = traceId(exchange);
        response.getHeaders().set("X-Request-Id", requestId);
        response.getHeaders().set("X-Trace-Id", traceId);
        DataBuffer buffer = response.bufferFactory().wrap(errorBody(requestId, traceId));
        return response.writeWith(Mono.just(buffer));
    }

    private byte[] errorBody(String requestId, String traceId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", "FORBIDDEN");
        body.put("message", "内网接口不对外开放");
        body.put("requestId", requestId);
        body.put("traceId", traceId);
        body.put("details", Map.of());
        body.put("timestamp", OffsetDateTime.now(ZoneOffset.UTC).toString());
        try {
            return objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException ex) {
            return FALLBACK_BODY;
        }
    }

    private String requestId(ServerWebExchange exchange) {
        String header = exchange.getRequest().getHeaders().getFirst("X-Request-Id");
        if (header != null && header.matches("[A-Za-z0-9_-]{8,64}")) {
            return header;
        }
        return "req_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private String traceId(ServerWebExchange exchange) {
        String traceparent = exchange.getRequest().getHeaders().getFirst("traceparent");
        if (traceparent != null) {
            String[] parts = traceparent.split("-");
            if (parts.length >= 3 && parts[1].matches("[0-9a-f]{32}")) {
                return parts[1];
            }
        }
        return UUID.randomUUID().toString().replace("-", "");
    }
}
