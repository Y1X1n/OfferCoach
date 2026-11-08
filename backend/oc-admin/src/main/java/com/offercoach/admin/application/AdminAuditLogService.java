package com.offercoach.admin.application;

import com.offercoach.admin.application.model.AdminModels.AuditLog;
import com.offercoach.admin.application.model.AdminModels.Paged;
import com.offercoach.admin.application.port.AdminAuditLogRepository;
import com.offercoach.admin.application.support.PageQuery;
import com.offercoach.common.error.BizException;
import com.offercoach.common.error.ErrorCode;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 审计日志只读查询。
 */
@Service
public class AdminAuditLogService {

    private final AdminAuditLogRepository auditLogRepository;

    public AdminAuditLogService(AdminAuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public Paged<AuditLog> list(int page, int size, String adminUserId, String action, String targetType) {
        PageQuery query = PageQuery.of(page, size);
        Long adminFilter = parseAdminUserId(adminUserId);
        String actionFilter = blankToNull(action);
        String targetFilter = blankToNull(targetType);
        List<AuditLog> items =
                auditLogRepository.search(adminFilter, actionFilter, targetFilter, (int) query.offset(), query.size());
        long total = auditLogRepository.count(adminFilter, actionFilter, targetFilter);
        return new Paged<>(items, total, query.page(), query.size());
    }

    private static Long parseAdminUserId(String adminUserId) {
        String value = blankToNull(adminUserId);
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of("adminUserId", "非数字 ID")));
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
