package com.offercoach.ai.tools.log;

import com.offercoach.common.trace.TraceContext;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 工具调用日志：追加 {@code ai_call_log}，失败只告警，不影响业务主流程。
 *
 * <p>这是工具服务唯一写入的业务库表，且为 append-only；不记录提示词、音频与识别原文。
 * 只有工具服务写日志，{@code oc-app} 侧关闭该 Bean 以避免同一次调用重复记录。</p>
 */
@Component
@ConditionalOnProperty(name = "oc.ai.tools.call-log-enabled", havingValue = "true", matchIfMissing = true)
public class ToolCallLogger {

    private static final Logger log = LoggerFactory.getLogger(ToolCallLogger.class);

    private final JdbcTemplate jdbcTemplate;
    private final MeterRegistry registry;

    public ToolCallLogger(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, null);
    }

    @Autowired
    public ToolCallLogger(JdbcTemplate jdbcTemplate, MeterRegistry registry) {
        this.jdbcTemplate = jdbcTemplate;
        this.registry = registry;
    }

    public void success(Long userId, String scene, String model, long latencyMs, Integer tokensIn, Integer tokensOut) {
        write(userId, scene, model, "SUCCESS", tokensIn, tokensOut, latencyMs, null);
    }

    public void degraded(Long userId, String scene, String model, long latencyMs, String errorCode) {
        write(userId, scene, model, "DEGRADED", null, null, latencyMs, errorCode);
    }

    public void failed(Long userId, String scene, String model, long latencyMs, String errorCode) {
        write(userId, scene, model, "FAILED", null, null, latencyMs, errorCode);
    }

    private void write(
            Long userId,
            String scene,
            String model,
            String status,
            Integer tokensIn,
            Integer tokensOut,
            long latencyMs,
            String errorCode) {
        recordMetrics(scene, status, latencyMs);
        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO ai_call_log
                        (user_id, scene, provider, model, status, tokens_in, tokens_out,
                         first_token_ms, latency_ms, error_code, request_id, trace_id,
                         session_id, created_at)
                    VALUES (?, ?, 'deepseek-tools', ?, ?, ?, ?, NULL, ?, ?, ?, ?, ?, now())
                    """,
                    userId == null || userId <= 0 ? null : userId,
                    scene,
                    model,
                    status,
                    tokensIn,
                    tokensOut,
                    Math.toIntExact(Math.min(Integer.MAX_VALUE, Math.max(0, latencyMs))),
                    errorCode,
                    TraceContext.requestId(),
                    TraceContext.traceId(),
                    sessionId());
        } catch (RuntimeException exception) {
            log.warn(
                    "工具调用日志写入失败：scene={} reason={}", scene, exception.getClass().getSimpleName());
        }
    }

    private void recordMetrics(String scene, String status, long latencyMs) {
        if (registry == null) {
            return;
        }
        String safeScene = scene == null || scene.isBlank() ? "unknown" : scene;
        String safeStatus = status == null || status.isBlank() ? "unknown" : status;
        registry.counter("oc.ai.calls", "provider", "deepseek-tools", "scene", safeScene, "status", safeStatus)
                .increment();
        registry.timer("oc.ai.latency", "provider", "deepseek-tools", "scene", safeScene, "status", safeStatus)
                .record(Duration.ofMillis(Math.max(0, latencyMs)));
    }

    private static Long sessionId() {
        String value = TraceContext.sessionId();
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
