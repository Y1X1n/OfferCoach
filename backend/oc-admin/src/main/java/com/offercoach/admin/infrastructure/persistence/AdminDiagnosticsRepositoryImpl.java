package com.offercoach.admin.infrastructure.persistence;

import com.offercoach.admin.application.model.AdminDiagnosticsModels.AiCallSnapshot;
import com.offercoach.admin.application.model.AdminDiagnosticsModels.AnswerSnapshot;
import com.offercoach.admin.application.model.AdminDiagnosticsModels.QuestionSnapshot;
import com.offercoach.admin.application.model.AdminDiagnosticsModels.ReportStats;
import com.offercoach.admin.application.model.AdminDiagnosticsModels.ReportTask;
import com.offercoach.admin.application.model.AdminDiagnosticsModels.SessionDiagnostics;
import com.offercoach.admin.application.model.AdminDiagnosticsModels.SessionSnapshot;
import com.offercoach.admin.application.port.AdminDiagnosticsRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 运行诊断只读查询：报告任务、卡住状态与会话时间线。
 */
@Repository
public class AdminDiagnosticsRepositoryImpl implements AdminDiagnosticsRepository {

    private final JdbcTemplate jdbcTemplate;

    public AdminDiagnosticsRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public ReportStats reportStats() {
        return jdbcTemplate.queryForObject(
                """
                SELECT
                  (SELECT COUNT(*) FROM interview_report WHERE status = 'GENERATING') AS pending_reports,
                  (SELECT COALESCE(EXTRACT(EPOCH FROM (now() - MIN(updated_at))), 0)::bigint
                     FROM interview_report WHERE status = 'GENERATING') AS oldest_pending_seconds,
                  (SELECT COUNT(*) FROM interview_report
                    WHERE status = 'FAILED' AND updated_at > now() - interval '24 hours') AS failed_reports_24h,
                  (SELECT COUNT(*) FROM interview_session
                    WHERE status = 'PREPARING' AND updated_at < now() - interval '10 minutes') AS stuck_sessions,
                  (SELECT COUNT(*) FROM interview_answer
                    WHERE status = 'SCORING' AND submitted_at < now() - interval '10 minutes') AS stale_answers,
                  (SELECT COUNT(*) FROM ai_call_log
                    WHERE status = 'FAILED' AND created_at > now() - interval '24 hours') AS ai_failures_24h
                """,
                (rs, rowNum) -> new ReportStats(
                        rs.getLong("pending_reports"),
                        rs.getLong("oldest_pending_seconds"),
                        rs.getLong("failed_reports_24h"),
                        rs.getLong("stuck_sessions"),
                        rs.getLong("stale_answers"),
                        rs.getLong("ai_failures_24h")));
    }

    @Override
    public List<ReportTask> pendingReports(int limit) {
        return reports("GENERATING", "ASC", limit);
    }

    @Override
    public List<ReportTask> failedReports(int limit) {
        return reports("FAILED", "DESC", limit);
    }

    private List<ReportTask> reports(String status, String order, int limit) {
        return jdbcTemplate.query(
                """
                SELECT id, session_id, user_id, attempts, last_error, updated_at,
                       EXTRACT(EPOCH FROM (now() - updated_at))::bigint AS age_seconds
                FROM interview_report
                WHERE status = ?
                ORDER BY updated_at
                """
                        + order + " LIMIT ?",
                (rs, rowNum) -> new ReportTask(
                        rs.getLong("id"),
                        rs.getLong("session_id"),
                        rs.getLong("user_id"),
                        rs.getInt("attempts"),
                        rs.getString("last_error"),
                        rs.getLong("age_seconds"),
                        rs.getObject("updated_at", OffsetDateTime.class)),
                status,
                limit);
    }

    @Override
    public Optional<SessionDiagnostics> session(long sessionId, int aiCallLimit) {
        Optional<SessionSnapshot> session = jdbcTemplate
                .query(
                        """
                        SELECT s.id, s.user_id, s.status, s.question_count, s.answered_count, s.current_seq,
                               s.total_score, s.failure_reason, s.created_at, s.last_active_at,
                               r.title AS resume_title, j.title AS jd_title
                        FROM interview_session s
                        LEFT JOIN resume r ON r.id = s.resume_id
                        LEFT JOIN job_description j ON j.id = s.jd_id
                        WHERE s.id = ?
                        """,
                        (rs, rowNum) -> new SessionSnapshot(
                                rs.getLong("id"),
                                rs.getLong("user_id"),
                                rs.getString("status"),
                                rs.getInt("question_count"),
                                rs.getInt("answered_count"),
                                rs.getInt("current_seq"),
                                decimal(rs.getObject("total_score", BigDecimal.class)),
                                rs.getString("failure_reason"),
                                rs.getString("resume_title"),
                                rs.getString("jd_title"),
                                rs.getObject("created_at", OffsetDateTime.class),
                                rs.getObject("last_active_at", OffsetDateTime.class)),
                        sessionId)
                .stream()
                .findFirst();
        if (session.isEmpty()) {
            return Optional.empty();
        }
        List<QuestionSnapshot> questions = jdbcTemplate.query(
                """
                SELECT id, seq, dimension, difficulty, source, status
                FROM interview_question
                WHERE session_id = ?
                ORDER BY seq
                """,
                (rs, rowNum) -> new QuestionSnapshot(
                        rs.getLong("id"),
                        rs.getInt("seq"),
                        rs.getString("dimension"),
                        rs.getString("difficulty"),
                        rs.getString("source"),
                        rs.getString("status")),
                sessionId);
        List<AnswerSnapshot> answers = jdbcTemplate.query(
                """
                SELECT id, question_id, status, input_mode, submitted_at
                FROM interview_answer
                WHERE session_id = ?
                ORDER BY submitted_at
                """,
                (rs, rowNum) -> new AnswerSnapshot(
                        rs.getLong("id"),
                        rs.getLong("question_id"),
                        rs.getString("status"),
                        rs.getString("input_mode"),
                        rs.getObject("submitted_at", OffsetDateTime.class)),
                sessionId);
        ReportTask report = jdbcTemplate
                .query(
                        """
                        SELECT id, session_id, user_id, attempts, last_error, updated_at,
                               EXTRACT(EPOCH FROM (now() - updated_at))::bigint AS age_seconds
                        FROM interview_report
                        WHERE session_id = ?
                        """,
                        (rs, rowNum) -> new ReportTask(
                                rs.getLong("id"),
                                rs.getLong("session_id"),
                                rs.getLong("user_id"),
                                rs.getInt("attempts"),
                                rs.getString("last_error"),
                                rs.getLong("age_seconds"),
                                rs.getObject("updated_at", OffsetDateTime.class)),
                        sessionId)
                .stream()
                .findFirst()
                .orElse(null);
        List<AiCallSnapshot> aiCalls = jdbcTemplate.query(
                """
                SELECT id, scene, provider, model, status, latency_ms, tokens_in, tokens_out,
                       error_code, request_id, prompt_version, created_at
                FROM ai_call_log
                WHERE session_id = ?
                ORDER BY created_at DESC
                LIMIT ?
                """,
                (rs, rowNum) -> new AiCallSnapshot(
                        rs.getLong("id"),
                        rs.getString("scene"),
                        rs.getString("provider"),
                        rs.getString("model"),
                        rs.getString("status"),
                        rs.getObject("latency_ms", Integer.class),
                        rs.getObject("tokens_in", Integer.class),
                        rs.getObject("tokens_out", Integer.class),
                        rs.getString("error_code"),
                        rs.getString("request_id"),
                        rs.getString("prompt_version"),
                        rs.getObject("created_at", OffsetDateTime.class)),
                sessionId,
                aiCallLimit);
        return Optional.of(new SessionDiagnostics(session.get(), questions, answers, report, aiCalls));
    }

    private static Double decimal(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}
