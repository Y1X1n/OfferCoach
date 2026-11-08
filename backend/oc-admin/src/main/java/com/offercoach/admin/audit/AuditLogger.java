package com.offercoach.admin.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offercoach.common.trace.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理端写操作审计；独立事务写入，序列化失败或写库失败只告警，不阻断主操作。
 */
@Component
public class AuditLogger {

    private static final Logger log = LoggerFactory.getLogger(AuditLogger.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AuditLogger(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(Long actorId, String action, String targetType, String targetId, Object before, Object after) {
        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO admin_audit_log
                        (admin_user_id, action, target_type, target_id, before_json, after_json, request_id, created_at)
                    VALUES (?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), ?, now())
                    """,
                    actorId,
                    action,
                    targetType,
                    targetId,
                    toJson(before),
                    toJson(after),
                    TraceContext.requestId());
        } catch (RuntimeException exception) {
            log.warn(
                    "审计日志写入失败：action={} reason={}", action, exception.getClass().getSimpleName());
        }
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            log.warn("审计日志序列化失败：reason={}", exception.getClass().getSimpleName());
            return null;
        }
    }
}
