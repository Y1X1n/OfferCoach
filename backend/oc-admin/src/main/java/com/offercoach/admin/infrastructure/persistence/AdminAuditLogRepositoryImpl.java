package com.offercoach.admin.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offercoach.admin.application.model.AdminModels.AuditLog;
import com.offercoach.admin.application.port.AdminAuditLogRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * 审计日志只读查询。
 */
@Repository
public class AdminAuditLogRepositoryImpl implements AdminAuditLogRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RowMapper<AuditLog> mapper = (rs, rowNum) -> new AuditLog(
            rs.getLong("id"),
            rs.getLong("admin_user_id"),
            rs.getString("action"),
            rs.getString("target_type"),
            rs.getString("target_id"),
            parseJson(rs.getString("before_json")),
            parseJson(rs.getString("after_json")),
            rs.getString("request_id"),
            rs.getObject("created_at", OffsetDateTime.class));

    public AdminAuditLogRepositoryImpl(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<AuditLog> search(Long adminUserId, String action, String targetType, int offset, int limit) {
        WhereClause where = filter(adminUserId, action, targetType);
        List<Object> args = new ArrayList<>(where.args());
        args.add(limit);
        args.add(offset);
        return jdbcTemplate.query(
                """
                SELECT id, admin_user_id, action, target_type, target_id,
                       before_json::text AS before_json, after_json::text AS after_json,
                       request_id, created_at
                FROM admin_audit_log
                """
                        + where.sql() + " ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?",
                mapper,
                args.toArray());
    }

    @Override
    public long count(Long adminUserId, String action, String targetType) {
        WhereClause where = filter(adminUserId, action, targetType);
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_audit_log" + where.sql(),
                Long.class,
                where.args().toArray());
        return count == null ? 0 : count;
    }

    private static WhereClause filter(Long adminUserId, String action, String targetType) {
        StringBuilder sql = new StringBuilder(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (adminUserId != null) {
            sql.append(" AND admin_user_id = ?");
            args.add(adminUserId);
        }
        if (action != null) {
            sql.append(" AND action = ?");
            args.add(action);
        }
        if (targetType != null) {
            sql.append(" AND target_type = ?");
            args.add(targetType);
        }
        return new WhereClause(sql.toString(), args);
    }

    private JsonNode parseJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("JSONB 解析失败", exception);
        }
    }

    private record WhereClause(String sql, List<Object> args) {}
}
