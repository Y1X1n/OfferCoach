package com.offercoach.admin.api;

import com.offercoach.admin.api.dto.AdminDiagnosticsDtos.ReportsResponse;
import com.offercoach.admin.api.dto.AdminDiagnosticsDtos.SessionResponse;
import com.offercoach.admin.application.AdminDiagnosticsService;
import com.offercoach.admin.application.model.AdminDiagnosticsModels.SessionDiagnostics;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端运行诊断端点：报告任务与单场会话时间线。
 */
@RestController
@RequestMapping("/api/v1/admin/diagnostics")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDiagnosticsController {

    private final AdminDiagnosticsService diagnosticsService;

    public AdminDiagnosticsController(AdminDiagnosticsService diagnosticsService) {
        this.diagnosticsService = diagnosticsService;
    }

    @GetMapping("/reports")
    public ReportsResponse reports(@RequestParam(value = "limit", defaultValue = "20") int limit) {
        AdminDiagnosticsService.ReportDiagnostics diagnostics = diagnosticsService.reports(limit);
        return new ReportsResponse(diagnostics.stats(), diagnostics.pending(), diagnostics.failed());
    }

    @GetMapping("/sessions/{sessionId}")
    public SessionResponse session(
            @PathVariable("sessionId") long sessionId,
            @RequestParam(value = "aiCallLimit", defaultValue = "20") int aiCallLimit) {
        SessionDiagnostics diagnostics = diagnosticsService.session(sessionId, aiCallLimit);
        return new SessionResponse(
                diagnostics.session(),
                diagnostics.questions(),
                diagnostics.answers(),
                diagnostics.report(),
                diagnostics.aiCalls());
    }
}
