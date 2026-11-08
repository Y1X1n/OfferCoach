package com.offercoach.admin.infrastructure.persistence;

import com.offercoach.admin.application.model.AdminModels.Metrics;
import com.offercoach.admin.application.port.AdminMetricsRepository;
import java.math.BigDecimal;
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
                activeUsersToday);
    }

    private long queryLong(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }
}
