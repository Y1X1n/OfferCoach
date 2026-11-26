package com.offercoach.interview.ai;

import com.offercoach.common.trace.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * AI 调用日志：写失败只告警，不影响业务主流程；不记录提示词与原文。
 *
 * <p>除用户与场景外，尽量写入 sessionId / requestId / traceId / promptVersion，
 * 便于从一场面试或一条错误日志回溯到具体模型调用。</p>
 */
@Component
public class AiCallLogger {

    private static final Logger log = LoggerFactory.getLogger(AiCallLogger.class);

    private final JdbcTemplate jdbcTemplate;
    private final InterviewMetrics metrics;

    public AiCallLogger(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, null);
    }

    @Autowired
    public AiCallLogger(JdbcTemplate jdbcTemplate, InterviewMetrics metrics) {
        this.jdbcTemplate = jdbcTemplate;
        this.metrics = metrics;
    }

    public void log(
            Long userId,
            String scene,
            String provider,
            String model,
            String status,
            Integer tokensIn,
            Integer tokensOut,
            Integer firstTokenMs,
            long latencyMs,
            String errorCode) {
        log(
                userId,
                scene,
                provider,
                model,
                status,
                tokensIn,
                tokensOut,
                firstTokenMs,
                latencyMs,
                errorCode,
                null,
                null);
    }

    public void log(
            Long userId,
            String scene,
            String provider,
            String model,
            String status,
            Integer tokensIn,
            Integer tokensOut,
            Integer firstTokenMs,
            long latencyMs,
            String errorCode,
            Long sessionId,
            String promptVersion) {
        if (metrics != null) {
            metrics.aiCall(provider, scene, status, latencyMs);
        }
        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO ai_call_log
                        (user_id, scene, provider, model, status, tokens_in, tokens_out,
                         first_token_ms, latency_ms, error_code, request_id, trace_id,
                         session_id, prompt_version, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                    """,
                    userId,
                    scene,
                    provider,
                    model,
                    status,
                    tokensIn,
                    tokensOut,
                    firstTokenMs,
                    Math.toIntExact(Math.min(Integer.MAX_VALUE, latencyMs)),
                    errorCode,
                    TraceContext.requestId(),
                    TraceContext.traceId(),
                    sessionId,
                    promptVersion);
        } catch (RuntimeException exception) {
            log.warn(
                    "AI 调用日志写入失败：scene={} reason={}",
                    scene,
                    exception.getClass().getSimpleName());
        }
    }
}
