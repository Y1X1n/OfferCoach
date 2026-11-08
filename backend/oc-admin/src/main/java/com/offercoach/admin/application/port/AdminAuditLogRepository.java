package com.offercoach.admin.application.port;

import com.offercoach.admin.application.model.AdminModels.AuditLog;
import java.util.List;

/**
 * 审计日志只读仓储。
 */
public interface AdminAuditLogRepository {

    List<AuditLog> search(Long adminUserId, String action, String targetType, int offset, int limit);

    long count(Long adminUserId, String action, String targetType);
}
