package com.offercoach.admin.infrastructure.persistence;

import com.offercoach.admin.application.model.AdminModels.User;
import com.offercoach.admin.application.port.AdminUserRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * 管理端用户查询：读取 app_user 并左连 user_quota 取套餐编码，不读取密码散列。
 */
@Repository
public class AdminUserRepositoryImpl implements AdminUserRepository {

    private static final String SELECT_COLUMNS =
            """
            u.id, u.email, u.nickname, u.role, u.status,
            COALESCE(q.plan_code, 'FREE') AS plan_code,
            q.interview_credits, q.resume_credits, u.created_at, u.last_login_at
            """;

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<User> userMapper = (rs, rowNum) -> new User(
            rs.getLong("id"),
            rs.getString("email"),
            rs.getString("nickname"),
            rs.getString("role"),
            rs.getString("status"),
            rs.getString("plan_code"),
            rs.getObject("interview_credits", Integer.class),
            rs.getObject("resume_credits", Integer.class),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("last_login_at", OffsetDateTime.class));

    public AdminUserRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<User> search(String keyword, String status, int offset, int limit) {
        WhereClause where = filter(keyword, status);
        List<Object> args = new ArrayList<>(where.args());
        args.add(limit);
        args.add(offset);
        return jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + " FROM app_user u"
                        + " LEFT JOIN user_quota q ON q.user_id = u.id" + where.sql()
                        + " ORDER BY u.created_at DESC, u.id DESC LIMIT ? OFFSET ?",
                userMapper,
                args.toArray());
    }

    @Override
    public long count(String keyword, String status) {
        WhereClause where = filter(keyword, status);
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM app_user u" + where.sql(),
                Long.class,
                where.args().toArray());
        return count == null ? 0 : count;
    }

    @Override
    public Optional<User> findById(long id) {
        return jdbcTemplate
                .query(
                        "SELECT " + SELECT_COLUMNS + " FROM app_user u"
                                + " LEFT JOIN user_quota q ON q.user_id = u.id"
                                + " WHERE u.id = ? AND u.deleted_at IS NULL",
                        userMapper,
                        id)
                .stream()
                .findFirst();
    }

    @Override
    public int updateStatus(long id, String status) {
        return jdbcTemplate.update(
                "UPDATE app_user SET status = ?, updated_at = now() WHERE id = ? AND deleted_at IS NULL", status, id);
    }

    @Override
    public int updateRole(long id, String role) {
        return jdbcTemplate.update(
                "UPDATE app_user SET role = ?, updated_at = now() WHERE id = ? AND deleted_at IS NULL", role, id);
    }

    @Override
    public int unlock(long id) {
        return jdbcTemplate.update(
                """
                UPDATE app_user
                SET status = CASE WHEN status = 'LOCKED' THEN 'ACTIVE' ELSE status END,
                    failed_login_count = 0,
                    locked_until = NULL,
                    updated_at = now()
                WHERE id = ? AND deleted_at IS NULL
                """,
                id);
    }

    @Override
    public int updatePassword(long id, String passwordHash) {
        return jdbcTemplate.update(
                "UPDATE app_user SET password_hash = ?, updated_at = now() WHERE id = ? AND deleted_at IS NULL",
                passwordHash,
                id);
    }

    @Override
    public long countAdmins() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM app_user WHERE role = 'ADMIN' AND deleted_at IS NULL AND status <> 'DELETED'",
                Long.class);
        return count == null ? 0 : count;
    }

    private static WhereClause filter(String keyword, String status) {
        StringBuilder sql = new StringBuilder(" WHERE u.deleted_at IS NULL");
        List<Object> args = new ArrayList<>();
        if (keyword != null) {
            sql.append(" AND (u.email::text ILIKE '%' || ? || '%' OR u.nickname ILIKE '%' || ? || '%')");
            args.add(keyword);
            args.add(keyword);
        }
        if (status != null) {
            sql.append(" AND u.status = ?");
            args.add(status);
        }
        return new WhereClause(sql.toString(), args);
    }

    private record WhereClause(String sql, List<Object> args) {}
}
