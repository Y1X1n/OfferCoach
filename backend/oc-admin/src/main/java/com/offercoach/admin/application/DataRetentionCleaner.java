package com.offercoach.admin.application;

import com.offercoach.admin.config.RetentionProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 数据保留策略：每天低峰清理过期的 AI 调用日志与管理端审计日志。
 *
 * <p>按批删除（每批 {@value #BATCH_SIZE} 行）避免单条大事务长时间持锁；时间基准取数据库 now()，
 * 与写入端默认值一致。
 */
@Service
public class DataRetentionCleaner {

    private static final Logger log = LoggerFactory.getLogger(DataRetentionCleaner.class);
    private static final int BATCH_SIZE = 5000;

    /** 依赖 idx_ai_call_time，子查询按 created_at 升序取批。 */
    private static final String PURGE_AI_CALL_SQL =
            """
            DELETE FROM ai_call_log
             WHERE id IN (
                 SELECT id
                   FROM ai_call_log
                  WHERE created_at < now() - make_interval(days => ?)
                  ORDER BY created_at
                  LIMIT ?
             )
            """;

    /** 依赖 idx_admin_audit_time。 */
    private static final String PURGE_AUDIT_SQL =
            """
            DELETE FROM admin_audit_log
             WHERE id IN (
                 SELECT id
                   FROM admin_audit_log
                  WHERE created_at < now() - make_interval(days => ?)
                  ORDER BY created_at
                  LIMIT ?
             )
            """;

    private final JdbcTemplate jdbcTemplate;
    private final RetentionProperties properties;
    private final TransactionTemplate transactionTemplate;

    public DataRetentionCleaner(
            JdbcTemplate jdbcTemplate, RetentionProperties properties, PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Scheduled(cron = "${oc.retention.cron:0 30 3 * * *}", zone = "${oc.retention.zone:Asia/Shanghai}")
    public void run() {
        purge("ai_call_log", PURGE_AI_CALL_SQL, properties.getAiCallDays());
        purgeAudit(properties.getAuditDays());
    }

    private void purge(String table, String sql, int days) {
        if (days <= 0) {
            log.warn("保留天数配置非法，跳过清理：table={} days={}", table, days);
            return;
        }
        long total = 0L;
        int batch = jdbcTemplate.update(sql, days, BATCH_SIZE);
        while (batch > 0) {
            total += batch;
            if (batch < BATCH_SIZE) {
                break;
            }
            batch = jdbcTemplate.update(sql, days, BATCH_SIZE);
        }
        log.info("保留策略清理完成：table={} retainDays={} deleted={}", table, days, total);
    }

    /**
     * 审计日志受数据库触发器保护，仅允许在带 app.audit_purge 标记的事务内删除。
     */
    private void purgeAudit(int days) {
        if (days <= 0) {
            log.warn("保留天数配置非法，跳过清理：table=admin_audit_log days={}", days);
            return;
        }
        long total = 0L;
        while (true) {
            Integer batch = transactionTemplate.execute(status -> {
                jdbcTemplate.execute("SET LOCAL app.audit_purge = 'on'");
                return jdbcTemplate.update(PURGE_AUDIT_SQL, days, BATCH_SIZE);
            });
            int deleted = batch == null ? 0 : batch;
            total += deleted;
            if (deleted < BATCH_SIZE) {
                break;
            }
        }
        log.info("保留策略清理完成：table=admin_audit_log retainDays={} deleted={}", days, total);
    }
}
