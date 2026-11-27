package com.offercoach.app.account;

import com.offercoach.common.error.BizException;
import com.offercoach.common.error.ErrorCode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 个人数据导出：只读聚合当前用户的主数据、额度流水、简历/JD、会话、回答与报告。
 *
 * <p>导出内容包含用户自己的原文，属于敏感数据；接口只允许当前登录用户访问，
 * 不写入日志，也不返回密码散列。</p>
 */
@Service
public class AccountDataExportService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AccountDataExportService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> export(long userId) {
        LinkedHashMap<String, Object> root = new LinkedHashMap<>();
        root.put("exportedAt", OffsetDateTime.now(ZoneOffset.UTC));
        root.put("user", user(userId));
        root.put("quota", quota(userId));
        root.put("quotaLedger", ledger(userId));
        root.put("resumes", resumes(userId));
        root.put("jds", jds(userId));
        root.put("sessions", sessions(userId));
        root.put("answers", answers(userId));
        root.put("reports", reports(userId));
        return root;
    }

    private Map<String, Object> user(long userId) {
        return jdbcTemplate
                .query(
                        """
                        SELECT id, email, nickname, role, timezone, created_at, last_login_at
                        FROM app_user WHERE id = ?
                        """,
                        (rs, rowNum) -> {
                            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
                            row.put("id", rs.getLong("id"));
                            row.put("email", rs.getString("email"));
                            row.put("nickname", rs.getString("nickname"));
                            row.put("role", rs.getString("role"));
                            row.put("timezone", rs.getString("timezone"));
                            row.put("createdAt", rs.getObject("created_at", OffsetDateTime.class));
                            row.put("lastLoginAt", rs.getObject("last_login_at", OffsetDateTime.class));
                            return row;
                        },
                        userId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private Map<String, Object> quota(long userId) {
        return jdbcTemplate
                .query(
                        """
                        SELECT plan_code, interview_credits, resume_credits, period_start, period_end
                        FROM user_quota WHERE user_id = ?
                        """,
                        (rs, rowNum) -> {
                            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
                            row.put("planCode", rs.getString("plan_code"));
                            row.put("interviewCredits", rs.getInt("interview_credits"));
                            row.put("resumeCredits", rs.getInt("resume_credits"));
                            row.put("periodStart", rs.getObject("period_start", java.time.LocalDate.class));
                            row.put("periodEnd", rs.getObject("period_end", java.time.LocalDate.class));
                            return row;
                        },
                        userId)
                .stream()
                .findFirst()
                .orElse(null);
    }

    private List<Map<String, Object>> ledger(long userId) {
        return jdbcTemplate.query(
                """
                SELECT quota_type, delta, balance_after, reason, ref_type, ref_id, created_at
                FROM quota_ledger WHERE user_id = ? ORDER BY created_at, id
                """,
                (rs, rowNum) -> {
                    LinkedHashMap<String, Object> row = new LinkedHashMap<>();
                    row.put("quotaType", rs.getString("quota_type"));
                    row.put("delta", rs.getInt("delta"));
                    row.put("balanceAfter", rs.getInt("balance_after"));
                    row.put("reason", rs.getString("reason"));
                    row.put("refType", rs.getString("ref_type"));
                    row.put("refId", rs.getObject("ref_id", Long.class));
                    row.put("createdAt", rs.getObject("created_at", OffsetDateTime.class));
                    return row;
                },
                userId);
    }

    private List<Map<String, Object>> resumes(long userId) {
        return jdbcTemplate.query(
                """
                SELECT id, title, source_type, file_name, parse_status, summary, raw_text,
                       structured_json, created_at
                FROM resume WHERE user_id = ? AND deleted_at IS NULL ORDER BY created_at
                """,
                (rs, rowNum) -> {
                    LinkedHashMap<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getLong("id"));
                    row.put("title", rs.getString("title"));
                    row.put("sourceType", rs.getString("source_type"));
                    row.put("fileName", rs.getString("file_name"));
                    row.put("parseStatus", rs.getString("parse_status"));
                    row.put("summary", rs.getString("summary"));
                    row.put("rawText", rs.getString("raw_text"));
                    row.put("structured", json(rs.getString("structured_json")));
                    row.put("createdAt", rs.getObject("created_at", OffsetDateTime.class));
                    return row;
                },
                userId);
    }

    private List<Map<String, Object>> jds(long userId) {
        return jdbcTemplate.query(
                """
                SELECT id, title, company, raw_text, structured_json, match_score, match_report, created_at
                FROM job_description WHERE user_id = ? AND deleted_at IS NULL ORDER BY created_at
                """,
                (rs, rowNum) -> {
                    LinkedHashMap<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getLong("id"));
                    row.put("title", rs.getString("title"));
                    row.put("company", rs.getString("company"));
                    row.put("rawText", rs.getString("raw_text"));
                    row.put("structured", json(rs.getString("structured_json")));
                    row.put("matchScore", rs.getBigDecimal("match_score"));
                    row.put("matchReport", json(rs.getString("match_report")));
                    row.put("createdAt", rs.getObject("created_at", OffsetDateTime.class));
                    return row;
                },
                userId);
    }

    private List<Map<String, Object>> sessions(long userId) {
        return jdbcTemplate.query(
                """
                SELECT id, status, config, question_count, answered_count, current_seq, total_score,
                       started_at, finished_at, created_at
                FROM interview_session WHERE user_id = ? ORDER BY created_at
                """,
                (rs, rowNum) -> {
                    LinkedHashMap<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getLong("id"));
                    row.put("status", rs.getString("status"));
                    row.put("config", json(rs.getString("config")));
                    row.put("questionCount", rs.getInt("question_count"));
                    row.put("answeredCount", rs.getInt("answered_count"));
                    row.put("currentSeq", rs.getInt("current_seq"));
                    row.put("totalScore", rs.getBigDecimal("total_score"));
                    row.put("startedAt", rs.getObject("started_at", OffsetDateTime.class));
                    row.put("finishedAt", rs.getObject("finished_at", OffsetDateTime.class));
                    row.put("createdAt", rs.getObject("created_at", OffsetDateTime.class));
                    return row;
                },
                userId);
    }

    private List<Map<String, Object>> answers(long userId) {
        return jdbcTemplate.query(
                """
                SELECT a.id, a.session_id, a.question_id, q.seq, q.dimension, q.question_text,
                       a.input_mode, a.answer_text, a.status, a.submitted_at
                FROM interview_answer a
                JOIN interview_question q ON q.id = a.question_id
                WHERE a.user_id = ?
                ORDER BY a.submitted_at
                """,
                (rs, rowNum) -> {
                    LinkedHashMap<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getLong("id"));
                    row.put("sessionId", rs.getLong("session_id"));
                    row.put("questionId", rs.getLong("question_id"));
                    row.put("seq", rs.getInt("seq"));
                    row.put("dimension", rs.getString("dimension"));
                    row.put("questionText", rs.getString("question_text"));
                    row.put("inputMode", rs.getString("input_mode"));
                    row.put("answerText", rs.getString("answer_text"));
                    row.put("status", rs.getString("status"));
                    row.put("submittedAt", rs.getObject("submitted_at", OffsetDateTime.class));
                    return row;
                },
                userId);
    }

    private List<Map<String, Object>> reports(long userId) {
        return jdbcTemplate.query(
                """
                SELECT id, session_id, status, overall_score, radar, summary, highlights, risks,
                       improvement_plan, question_breakdown, generated_at, created_at
                FROM interview_report WHERE user_id = ? ORDER BY created_at
                """,
                (rs, rowNum) -> {
                    LinkedHashMap<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getLong("id"));
                    row.put("sessionId", rs.getLong("session_id"));
                    row.put("status", rs.getString("status"));
                    row.put("overallScore", rs.getBigDecimal("overall_score"));
                    row.put("radar", json(rs.getString("radar")));
                    row.put("summary", rs.getString("summary"));
                    row.put("highlights", json(rs.getString("highlights")));
                    row.put("risks", json(rs.getString("risks")));
                    row.put("improvementPlan", json(rs.getString("improvement_plan")));
                    row.put("questionBreakdown", json(rs.getString("question_breakdown")));
                    row.put("generatedAt", rs.getObject("generated_at", OffsetDateTime.class));
                    row.put("createdAt", rs.getObject("created_at", OffsetDateTime.class));
                    return row;
                },
                userId);
    }

    private JsonNode json(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (JacksonException exception) {
            return null;
        }
    }
}
