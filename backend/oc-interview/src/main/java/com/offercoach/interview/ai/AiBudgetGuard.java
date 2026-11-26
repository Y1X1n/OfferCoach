package com.offercoach.interview.ai;

import com.offercoach.ai.AiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 全局日 token 预算熔断：超过 {@code oc.ai.daily-token-budget} 后不再调用模型，
 * 由 {@link AiGateway} 返回不可用，业务走模板/启发式降级。
 *
 * <p>预算按当日 ai_call_log 的 token 用量估算，缓存 30 秒，避免每次调用都扫表；
 * 0 表示不限制。真实账单仍以供应商为准。</p>
 */
@Component
public class AiBudgetGuard {

    private static final Logger log = LoggerFactory.getLogger(AiBudgetGuard.class);
    private static final long CACHE_TTL_MILLIS = 30_000L;

    private final JdbcTemplate jdbcTemplate;
    private final AiProperties properties;

    private volatile long cachedSpent;
    private volatile long cachedAtMillis;

    public AiBudgetGuard(JdbcTemplate jdbcTemplate, AiProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    public boolean exhausted() {
        long budget = properties.getDailyTokenBudget();
        if (budget <= 0) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (cachedAtMillis > 0 && now - cachedAtMillis < CACHE_TTL_MILLIS) {
            return cachedSpent >= budget;
        }
        try {
            Long spent = jdbcTemplate.queryForObject(
                    """
                    SELECT COALESCE(SUM(COALESCE(tokens_in, 0) + COALESCE(tokens_out, 0)), 0)
                    FROM ai_call_log
                    WHERE created_at >= date_trunc('day', now())
                    """,
                    Long.class);
            cachedSpent = spent == null ? 0 : spent;
            cachedAtMillis = now;
            if (cachedSpent >= budget) {
                log.warn("AI 日 token 预算已用尽：spent={} budget={}", cachedSpent, budget);
                return true;
            }
            return false;
        } catch (RuntimeException exception) {
            // 预算表不可用时放行，避免因统计故障阻断业务。
            log.warn("AI 预算统计失败，按未超限处理：reason={}", exception.getClass().getSimpleName());
            return false;
        }
    }
}
