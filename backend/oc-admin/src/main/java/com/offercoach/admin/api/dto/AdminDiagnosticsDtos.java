package com.offercoach.admin.api.dto;

import com.offercoach.admin.application.model.AdminDiagnosticsModels.AiCallSnapshot;
import com.offercoach.admin.application.model.AdminDiagnosticsModels.AnswerSnapshot;
import com.offercoach.admin.application.model.AdminDiagnosticsModels.QuestionSnapshot;
import com.offercoach.admin.application.model.AdminDiagnosticsModels.ReportStats;
import com.offercoach.admin.application.model.AdminDiagnosticsModels.ReportTask;
import com.offercoach.admin.application.model.AdminDiagnosticsModels.SessionSnapshot;
import java.util.List;

/**
 * 运行诊断响应；只读，不包含回答正文与简历原文。
 */
public final class AdminDiagnosticsDtos {

    private AdminDiagnosticsDtos() {}

    public record ReportsResponse(ReportStats stats, List<ReportTask> pending, List<ReportTask> failed) {}

    public record SessionResponse(
            SessionSnapshot session,
            List<QuestionSnapshot> questions,
            List<AnswerSnapshot> answers,
            ReportTask report,
            List<AiCallSnapshot> aiCalls) {}
}
