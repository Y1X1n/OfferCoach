package com.offercoach.admin.application.port;

import com.offercoach.admin.application.model.AdminDiagnosticsModels.ReportStats;
import com.offercoach.admin.application.model.AdminDiagnosticsModels.ReportTask;
import com.offercoach.admin.application.model.AdminDiagnosticsModels.SessionDiagnostics;
import java.util.List;
import java.util.Optional;

/**
 * 运行诊断只读仓储。
 */
public interface AdminDiagnosticsRepository {

    ReportStats reportStats();

    List<ReportTask> pendingReports(int limit);

    List<ReportTask> failedReports(int limit);

    Optional<SessionDiagnostics> session(long sessionId, int aiCallLimit);
}
