package com.offercoach.interview.dashboard.api;

import com.offercoach.identity.security.CurrentUser;
import com.offercoach.interview.dashboard.api.dto.DashboardResponse;
import com.offercoach.interview.dashboard.application.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工作台聚合端点：一次请求返回额度、简历/JD 概览、进行中会话与最近报告。
 */
@RestController
@RequestMapping("/api/v1")
public class DashboardController {

    private final DashboardService dashboardService;
    private final CurrentUser currentUser;

    public DashboardController(DashboardService dashboardService, CurrentUser currentUser) {
        this.dashboardService = dashboardService;
        this.currentUser = currentUser;
    }

    @GetMapping("/dashboard")
    public DashboardResponse dashboard() {
        return toResponse(dashboardService.load(currentUser.userId()));
    }

    private static DashboardResponse toResponse(DashboardService.DashboardView view) {
        return new DashboardResponse(
                new DashboardResponse.Quota(
                        view.quota().planCode(),
                        view.quota().planName(),
                        view.quota().interviewCredits(),
                        view.quota().resumeCredits(),
                        view.quota().periodEnd(),
                        new DashboardResponse.Daily(
                                view.quota().daily().date(),
                                view.quota().daily().sessionsUsed(),
                                view.quota().daily().sessionsLimit()),
                        view.quota().features()),
                view.resumeReady(),
                view.jdCount(),
                view.activeSession() == null
                        ? null
                        : new DashboardResponse.ActiveSession(
                                view.activeSession().id(),
                                view.activeSession().status(),
                                view.activeSession().resumeId(),
                                view.activeSession().jdId(),
                                view.activeSession().resumeTitle(),
                                view.activeSession().jdTitle(),
                                view.activeSession().jdCompany(),
                                view.activeSession().questionCount(),
                                view.activeSession().answeredCount(),
                                view.activeSession().startedAt(),
                                view.activeSession().lastActiveAt()),
                view.recentReports().stream()
                        .map(report -> new DashboardResponse.Report(
                                report.id(),
                                report.sessionId(),
                                report.status(),
                                report.overallScore(),
                                report.summary(),
                                report.title(),
                                report.company(),
                                report.role(),
                                report.questionCount(),
                                report.createdAt()))
                        .toList(),
                view.reportTotal(),
                view.averageScore(),
                view.scoredReportCount());
    }
}
