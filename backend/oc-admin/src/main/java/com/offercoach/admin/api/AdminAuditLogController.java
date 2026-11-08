package com.offercoach.admin.api;

import com.offercoach.admin.api.dto.AdminAuditLogDtos.AuditLogResponse;
import com.offercoach.admin.api.dto.PageResponse;
import com.offercoach.admin.application.AdminAuditLogService;
import com.offercoach.admin.application.model.AdminModels.AuditLog;
import com.offercoach.admin.application.model.AdminModels.Paged;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审计日志只读端点。
 */
@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAuditLogController {

    private final AdminAuditLogService auditLogService;

    public AdminAuditLogController(AdminAuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public PageResponse<AuditLogResponse> list(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "adminUserId", required = false) String adminUserId,
            @RequestParam(value = "action", required = false) String action,
            @RequestParam(value = "targetType", required = false) String targetType) {
        Paged<AuditLog> result = auditLogService.list(page, size, adminUserId, action, targetType);
        return PageResponse.of(
                result.items().stream().map(AdminAuditLogController::toResponse).toList(),
                result.total(),
                result.page(),
                result.size());
    }

    private static AuditLogResponse toResponse(AuditLog log) {
        return new AuditLogResponse(
                Long.toString(log.id()),
                Long.toString(log.adminUserId()),
                log.action(),
                log.targetType(),
                log.targetId(),
                log.before(),
                log.after(),
                log.requestId(),
                AdminWebSupport.timestamp(log.createdAt()));
    }
}
