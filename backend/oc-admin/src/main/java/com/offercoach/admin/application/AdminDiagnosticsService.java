package com.offercoach.admin.application;

import com.offercoach.admin.application.model.AdminDiagnosticsModels.ReportStats;
import com.offercoach.admin.application.model.AdminDiagnosticsModels.ReportTask;
import com.offercoach.admin.application.model.AdminDiagnosticsModels.SessionDiagnostics;
import com.offercoach.admin.application.port.AdminDiagnosticsRepository;
import com.offercoach.common.error.BizException;
import com.offercoach.common.error.ErrorCode;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 管理端运行诊断：报告积压/失败与单场会话时间线。
 */
@Service
public class AdminDiagnosticsService {

    private static final int MAX_LIMIT = 50;

    private final AdminDiagnosticsRepository diagnosticsRepository;

    public AdminDiagnosticsService(AdminDiagnosticsRepository diagnosticsRepository) {
        this.diagnosticsRepository = diagnosticsRepository;
    }

    public ReportDiagnostics reports(int limit) {
        int safeLimit = Math.min(Math.max(1, limit), MAX_LIMIT);
        return new ReportDiagnostics(
                diagnosticsRepository.reportStats(),
                diagnosticsRepository.pendingReports(safeLimit),
                diagnosticsRepository.failedReports(safeLimit));
    }

    public SessionDiagnostics session(long sessionId, int aiCallLimit) {
        int safeLimit = Math.min(Math.max(1, aiCallLimit), MAX_LIMIT);
        return diagnosticsRepository
                .session(sessionId, safeLimit)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("sessionId", sessionId)));
    }

    public record ReportDiagnostics(ReportStats stats, List<ReportTask> pending, List<ReportTask> failed) {}
}
