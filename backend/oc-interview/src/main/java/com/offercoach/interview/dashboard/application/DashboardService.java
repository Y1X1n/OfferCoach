package com.offercoach.interview.dashboard.application;

import com.offercoach.identity.application.quota.QuotaService;
import com.offercoach.identity.application.quota.QuotaSnapshot;
import com.offercoach.interview.jd.application.port.JobDescriptionRepository;
import com.offercoach.interview.jd.domain.JobDescription;
import com.offercoach.interview.report.application.ReportMetaResolver;
import com.offercoach.interview.report.application.port.InterviewReportRepository;
import com.offercoach.interview.report.domain.InterviewReport;
import com.offercoach.interview.resume.application.port.ResumeRepository;
import com.offercoach.interview.resume.domain.Resume;
import com.offercoach.interview.session.application.port.InterviewSessionRepository;
import com.offercoach.interview.session.domain.InterviewSession;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 工作台聚合：一次返回额度、简历/JD 概览、进行中会话与最近报告，替代多路分页请求。
 */
@Service
public class DashboardService {

    private static final int REPORT_WINDOW = 20;
    private static final int RECENT_REPORT_LIMIT = 4;

    private final QuotaService quotaService;
    private final ResumeRepository resumeRepository;
    private final JobDescriptionRepository jdRepository;
    private final InterviewSessionRepository sessionRepository;
    private final InterviewReportRepository reportRepository;
    private final ReportMetaResolver reportMetaResolver;

    public DashboardService(
            QuotaService quotaService,
            ResumeRepository resumeRepository,
            JobDescriptionRepository jdRepository,
            InterviewSessionRepository sessionRepository,
            InterviewReportRepository reportRepository,
            ReportMetaResolver reportMetaResolver) {
        this.quotaService = quotaService;
        this.resumeRepository = resumeRepository;
        this.jdRepository = jdRepository;
        this.sessionRepository = sessionRepository;
        this.reportRepository = reportRepository;
        this.reportMetaResolver = reportMetaResolver;
    }

    @Transactional(readOnly = true)
    public DashboardView load(long userId) {
        QuotaSnapshot quota = quotaService.snapshot(userId);
        boolean resumeReady = resumeRepository.countReady(userId) > 0;
        long jdCount = jdRepository.count(userId);
        ActiveSessionView activeSession = sessionRepository
                .findLatestOpen(userId)
                .map(session -> toActiveSession(userId, session))
                .orElse(null);

        long reportTotal = reportRepository.count(userId);
        List<InterviewReport> window = reportRepository.list(userId, 0, REPORT_WINDOW);
        BigDecimal scoreSum = BigDecimal.ZERO;
        int scoredCount = 0;
        List<ReportView> recentReports = new ArrayList<>();
        for (InterviewReport report : window) {
            if (report.overallScore() != null) {
                scoreSum = scoreSum.add(report.overallScore());
                scoredCount++;
            }
            if (recentReports.size() < RECENT_REPORT_LIMIT) {
                recentReports.add(toReportView(userId, report));
            }
        }
        BigDecimal averageScore =
                scoredCount == 0 ? null : scoreSum.divide(BigDecimal.valueOf(scoredCount), 1, RoundingMode.HALF_UP);

        return new DashboardView(
                toQuotaView(quota),
                resumeReady,
                jdCount,
                activeSession,
                recentReports,
                reportTotal,
                averageScore,
                scoredCount);
    }

    private ActiveSessionView toActiveSession(long userId, InterviewSession session) {
        Optional<JobDescription> jd = jdRepository.findOwned(userId, session.jdId());
        Optional<Resume> resume = resumeRepository.findOwned(userId, session.resumeId());
        return new ActiveSessionView(
                Long.toString(session.id()),
                session.status(),
                Long.toString(session.resumeId()),
                Long.toString(session.jdId()),
                resume.map(Resume::title).orElse(null),
                jd.map(JobDescription::title).orElse(null),
                jd.map(JobDescription::company).orElse(null),
                session.questionCount(),
                session.answeredCount(),
                timestamp(session.startedAt()),
                timestamp(session.lastActiveAt()));
    }

    private ReportView toReportView(long userId, InterviewReport report) {
        ReportMetaResolver.Meta meta = reportMetaResolver.resolve(userId, report.sessionId());
        return new ReportView(
                Long.toString(report.id()),
                Long.toString(report.sessionId()),
                report.status(),
                report.overallScore(),
                report.summary(),
                meta.title(),
                meta.company(),
                meta.role(),
                meta.questionCount(),
                timestamp(report.createdAt()));
    }

    private static QuotaView toQuotaView(QuotaSnapshot snapshot) {
        return new QuotaView(
                snapshot.planCode(),
                snapshot.planName(),
                snapshot.interviewCredits(),
                snapshot.resumeCredits(),
                snapshot.periodEnd(),
                new DailyView(snapshot.dailyDate(), snapshot.sessionsUsed(), snapshot.sessionsLimit()),
                snapshot.features());
    }

    private static String timestamp(OffsetDateTime value) {
        return value == null ? null : value.toInstant().toString();
    }

    public record DashboardView(
            QuotaView quota,
            boolean resumeReady,
            long jdCount,
            ActiveSessionView activeSession,
            List<ReportView> recentReports,
            long reportTotal,
            BigDecimal averageScore,
            int scoredReportCount) {}

    public record QuotaView(
            String planCode,
            String planName,
            int interviewCredits,
            int resumeCredits,
            java.time.LocalDate periodEnd,
            DailyView daily,
            Map<String, Object> features) {}

    public record DailyView(java.time.LocalDate date, int sessionsUsed, int sessionsLimit) {}

    public record ActiveSessionView(
            String id,
            String status,
            String resumeId,
            String jdId,
            String resumeTitle,
            String jdTitle,
            String jdCompany,
            int questionCount,
            int answeredCount,
            String startedAt,
            String lastActiveAt) {}

    public record ReportView(
            String id,
            String sessionId,
            String status,
            BigDecimal overallScore,
            String summary,
            String title,
            String company,
            String role,
            int questionCount,
            String createdAt) {}
}
