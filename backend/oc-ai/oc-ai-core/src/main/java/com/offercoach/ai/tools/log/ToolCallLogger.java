package com.offercoach.ai.tools.log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public ToolCallLogger(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
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
        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO ai_call_log
                        (user_id, scene, provider, model, status, tokens_in, tokens_out,
                         first_token_ms, latency_ms, error_code, created_at)
                    VALUES (?, ?, 'deepseek-tools', ?, ?, ?, ?, NULL, ?, ?, now())
                    """,
                    userId == null || userId <= 0 ? null : userId,
                    scene,
                    model,
                    status,
                    tokensIn,
                    tokensOut,
                    Math.toIntExact(Math.min(Integer.MAX_VALUE, Math.max(0, latencyMs))),
                    errorCode);
        } catch (RuntimeException exception) {
            log.warn(
                    "工具调用日志写入失败：scene={} reason={}", scene, exception.getClass().getSimpleName());
        }
    }
}
