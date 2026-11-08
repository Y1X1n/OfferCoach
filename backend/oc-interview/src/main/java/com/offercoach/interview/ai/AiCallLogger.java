package com.offercoach.interview.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * AI 调用日志：写失败只告警，不影响业务主流程；不记录提示词与原文。
 */
@Component
public class AiCallLogger {

    private static final Logger log = LoggerFactory.getLogger(AiCallLogger.class);

    private final JdbcTemplate jdbcTemplate;

    public AiCallLogger(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
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
        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO ai_call_log
                        (user_id, scene, provider, model, status, tokens_in, tokens_out,
                         first_token_ms, latency_ms, error_code, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
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
                    errorCode);
        } catch (RuntimeException exception) {
            log.warn(
                    "AI 调用日志写入失败：scene={} reason={}",
                    scene,
                    exception.getClass().getSimpleName());
        }
    }
}
