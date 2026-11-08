package com.offercoach.admin.infrastructure.persistence;

import com.offercoach.admin.application.model.AdminModels.AiCall;
import com.offercoach.admin.application.port.AdminAiCallRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * AI 调用日志只读查询。
 */
@Repository
public class AdminAiCallRepositoryImpl implements AdminAiCallRepository {

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<AiCall> mapper = (rs, rowNum) -> new AiCall(
            rs.getLong("id"),
            rs.getObject("user_id", Long.class),
            rs.getString("scene"),
            rs.getString("provider"),
            rs.getString("model"),
            rs.getString("status"),
            rs.getObject("tokens_in", Integer.class),
            rs.getObject("tokens_out", Integer.class),
            rs.getObject("first_token_ms", Integer.class),
            rs.getObject("latency_ms", Integer.class),
            rs.getString("error_code"),
            rs.getObject("created_at", OffsetDateTime.class));

    public AdminAiCallRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<AiCall> search(String scene, String status, Long userId, int offset, int limit) {
        WhereClause where = filter(scene, status, userId);
        List<Object> args = new ArrayList<>(where.args());
        args.add(limit);
        args.add(offset);
        return jdbcTemplate.query(
                """
                SELECT id, user_id, scene, provider, model, status, tokens_in, tokens_out,
                       first_token_ms, latency_ms, error_code, created_at
                FROM ai_call_log
                """
                        + where.sql() + " ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?",
                mapper,
                args.toArray());
    }

    @Override
    public long count(String scene, String status, Long userId) {
        WhereClause where = filter(scene, status, userId);
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_call_log" + where.sql(),
                Long.class,
                where.args().toArray());
        return count == null ? 0 : count;
    }

    private static WhereClause filter(String scene, String status, Long userId) {
        StringBuilder sql = new StringBuilder(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (scene != null) {
            sql.append(" AND scene = ?");
            args.add(scene);
        }
        if (status != null) {
            sql.append(" AND status = ?");
            args.add(status);
        }
        if (userId != null) {
            sql.append(" AND user_id = ?");
            args.add(userId);
        }
        return new WhereClause(sql.toString(), args);
    }

    private record WhereClause(String sql, List<Object> args) {}
}
