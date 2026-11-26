package com.offercoach.admin.infrastructure.persistence;

import com.offercoach.admin.application.model.AdminModels.AiUsage;
import com.offercoach.admin.application.model.AdminModels.Metrics;
import com.offercoach.admin.application.model.AdminModels.ModelTokens;
import com.offercoach.admin.application.port.AdminMetricsRepository;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 运营指标聚合：时间窗口统一使用 make_interval(days => ?)，不拼接用户输入。
 */
@Repository
public class AdminMetricsRepositoryImpl implements AdminMetricsRepository {

    private final JdbcTemplate jdbcTemplate;

    public AdminMetricsRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Metrics overview(int rangeDays) {
        long totalUsers = queryLong("SELECT COUNT(*) FROM app_user WHERE deleted_at IS NULL");
        long newUsers = queryLong(
                """
                SELECT COUNT(*) FROM app_user
                WHERE deleted_at IS NULL AND created_at >= now() - make_interval(days => ?)
                """,
                rangeDays);
        long sessions = queryLong(
                "SELECT COUNT(*) FROM interview_session WHERE created_at >= now() - make_interval(days => ?)",
                rangeDays);
        long completedSessions = queryLong(
                """
                SELECT COUNT(*) FROM interview_session
                WHERE status = 'COMPLETED' AND created_at >= now() - make_interval(days => ?)
                """,
                rangeDays);
        long reports = queryLong(
                "SELECT COUNT(*) FROM interview_report WHERE created_at >= now() - make_interval(days => ?)",
                rangeDays);
        BigDecimal averageScore = jdbcTemplate.queryForObject(
                """
                SELECT ROUND(COALESCE(AVG(overall_score), 0), 1) FROM interview_report
                WHERE status = 'READY' AND created_at >= now() - make_interval(days => ?)
                """,
                BigDecimal.class,
                rangeDays);
        long aiCalls = queryLong(
                "SELECT COUNT(*) FROM ai_call_log WHERE created_at >= now() - make_interval(days => ?)", rangeDays);
        long aiFailures = queryLong(
                """
                SELECT COUNT(*) FROM ai_call_log
                WHERE status = 'FAILED' AND created_at >= now() - make_interval(days => ?)
                """,
                rangeDays);
        long activeUsersToday = queryLong(
                """
                SELECT COUNT(DISTINCT user_id) FROM (
                    SELECT user_id FROM interview_session
                    WHERE last_active_at >= date_trunc('day', now())
                    UNION
                    SELECT id AS user_id FROM app_user
                    WHERE deleted_at IS NULL AND last_login_at >= date_trunc('day', now())
                ) active
                """);
        return new Metrics(
                totalUsers,
                newUsers,
                sessions,
                completedSessions,
                reports,
                averageScore == null ? BigDecimal.ZERO : averageScore,
                aiCalls,
                aiFailures,
                0,
                0,
                0,
                null,
                activeUsersToday);
    }

    @Override
    public AiUsage aiUsage(int rangeDays) {
        Map<String, ModelTokens> byModel = new LinkedHashMap<>();
        long[] totals = new long[2];
        jdbcTemplate.query(
                """
                SELECT model,
                       COALESCE(SUM(tokens_in), 0)::bigint AS tokens_in,
                       COALESCE(SUM(tokens_out), 0)::bigint AS tokens_out
                FROM ai_call_log
                WHERE created_at >= now() - make_interval(days => ?)
                GROUP BY model
                """,
                rs -> {
                    long tokensIn = rs.getLong("tokens_in");
                    long tokensOut = rs.getLong("tokens_out");
                    byModel.put(rs.getString("model"), new ModelTokens(tokensIn, tokensOut));
                    totals[0] += tokensIn;
                    totals[1] += tokensOut;
                },
                rangeDays);
        Integer latencyP95 = jdbcTemplate.queryForObject(
                """
                SELECT ROUND(PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY latency_ms))::int
                FROM ai_call_log
                WHERE latency_ms IS NOT NULL AND created_at >= now() - make_interval(days => ?)
                """,
                Integer.class,
                rangeDays);
        return new AiUsage(totals[0], totals[1], latencyP95, byModel);
    }

    private long queryLong(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }
}
