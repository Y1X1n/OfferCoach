package com.offercoach.interview.dashboard.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.offercoach.identity.application.quota.QuotaService;
import com.offercoach.identity.application.quota.QuotaSnapshot;
import com.offercoach.interview.jd.application.port.JobDescriptionRepository;
import com.offercoach.interview.report.application.ReportMetaResolver;
import com.offercoach.interview.report.application.port.InterviewReportRepository;
import com.offercoach.interview.report.domain.InterviewReport;
import com.offercoach.interview.resume.application.port.ResumeRepository;
import com.offercoach.interview.session.application.port.InterviewSessionRepository;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

/**
 * 工作台聚合的事务语义回归：{@code load} 不能是只读事务。
 *
 * <p>{@code QuotaService.snapshot} 在跨天/跨月/首次访问时会写 {@code user_quota}；
 * 只读事务下 pgjdbc 会发 {@code SET TRANSACTION READ ONLY}，UPDATE 直接报
 * "cannot execute UPDATE in a read-only transaction"，工作台每天 0 点后稳定 500。</p>
 */
class DashboardServiceTest {

    @Test
    void loadMustNotBeReadOnlyTransaction() throws NoSuchMethodException {
        Method load = DashboardService.class.getMethod("load", long.class);
        Transactional annotation = load.getAnnotation(Transactional.class);

        assertThat(annotation).as("load 需要事务").isNotNull();
        assertThat(annotation.readOnly())
                .as("load 会通过 QuotaService.snapshot 写 user_quota，不能声明 readOnly")
                .isFalse();
    }

    @Test
    void loadAggregatesQuotaResumeJdAndReports() {
        QuotaService quotaService = mock(QuotaService.class);
        ResumeRepository resumeRepository = mock(ResumeRepository.class);
        JobDescriptionRepository jdRepository = mock(JobDescriptionRepository.class);
        InterviewSessionRepository sessionRepository = mock(InterviewSessionRepository.class);
        InterviewReportRepository reportRepository = mock(InterviewReportRepository.class);
        ReportMetaResolver metaResolver = mock(ReportMetaResolver.class);

        when(quotaService.snapshot(anyLong()))
                .thenReturn(new QuotaSnapshot(
                        "FREE", "免费版", 3, 2, LocalDate.now().plusDays(20), LocalDate.now(), 1, 5, Map.of()));
        when(resumeRepository.countReady(anyLong())).thenReturn(1L);
        when(jdRepository.count(anyLong())).thenReturn(2L);
        when(sessionRepository.findLatestOpen(anyLong())).thenReturn(Optional.empty());
        when(reportRepository.count(anyLong())).thenReturn(0L);
        when(reportRepository.list(anyLong(), eq(0), eq(20))).thenReturn(List.of());
        when(metaResolver.resolveAll(anyLong(), any())).thenReturn(Map.of());

        DashboardService service = new DashboardService(
                quotaService, resumeRepository, jdRepository, sessionRepository, reportRepository, metaResolver);

        DashboardService.DashboardView view = service.load(7L);

        assertThat(view.quota().interviewCredits()).isEqualTo(3);
        assertThat(view.resumeReady()).isTrue();
        assertThat(view.jdCount()).isEqualTo(2L);
        assertThat(view.activeSession()).isNull();
        assertThat(view.recentReports()).isEmpty();
    }

    /**
     * 最近报告必须一次批量解析：逐份 resolve 时 4 段报告就是 12 条查询，且都在同一个事务里。
     *
     * <p>顺带钉住两点：元信息只取窗口里的前 4 份（{@code RECENT_REPORT_LIMIT}），而平均分
     * 用的是整个 20 份窗口。</p>
     */
    @Test
    void recentReportsResolveMetaInOneBatch() {
        QuotaService quotaService = mock(QuotaService.class);
        ResumeRepository resumeRepository = mock(ResumeRepository.class);
        JobDescriptionRepository jdRepository = mock(JobDescriptionRepository.class);
        InterviewSessionRepository sessionRepository = mock(InterviewSessionRepository.class);
        InterviewReportRepository reportRepository = mock(InterviewReportRepository.class);
        ReportMetaResolver metaResolver = mock(ReportMetaResolver.class);

        when(quotaService.snapshot(anyLong()))
                .thenReturn(new QuotaSnapshot(
                        "FREE", "免费版", 3, 2, LocalDate.now().plusDays(20), LocalDate.now(), 1, 5, Map.of()));
        when(sessionRepository.findLatestOpen(anyLong())).thenReturn(Optional.empty());
        when(reportRepository.count(anyLong())).thenReturn(5L);
        when(reportRepository.list(anyLong(), eq(0), eq(20)))
                .thenReturn(List.of(
                        report(1L, 101L, "80.0"),
                        report(2L, 102L, "90.0"),
                        report(3L, 103L, "70.0"),
                        report(4L, 104L, "60.0"),
                        report(5L, 105L, null)));
        when(metaResolver.resolveAll(7L, List.of(101L, 102L, 103L, 104L)))
                .thenReturn(Map.of(
                        101L, new ReportMetaResolver.Meta("张三的简历", "某科技", "后端工程师", 5),
                        102L, new ReportMetaResolver.Meta("李四的简历", "某数据", "数据工程师", 4),
                        103L, new ReportMetaResolver.Meta("王五的简历", "某支付", "支付工程师", 3),
                        104L, new ReportMetaResolver.Meta("赵六的简历", "某电商", "电商工程师", 2)));

        DashboardService service = new DashboardService(
                quotaService, resumeRepository, jdRepository, sessionRepository, reportRepository, metaResolver);
        DashboardService.DashboardView view = service.load(7L);

        // 一次批量、覆盖窗口前 4 份会话；没有任何逐份调用
        verify(metaResolver).resolveAll(7L, List.of(101L, 102L, 103L, 104L));
        verify(metaResolver, never()).resolve(anyLong(), anyLong());

        assertThat(view.recentReports()).hasSize(4);
        assertThat(view.recentReports().getFirst().title()).isEqualTo("张三的简历");
        assertThat(view.recentReports().getFirst().company()).isEqualTo("某科技");
        assertThat(view.recentReports().getFirst().questionCount()).isEqualTo(5);
        assertThat(view.recentReports().getLast().sessionId()).isEqualTo("104");
        // 平均分按 20 份窗口算（4 份有分），不是按前 4 份
        assertThat(view.averageScore()).isEqualByComparingTo("75.0");
        assertThat(view.scoredReportCount()).isEqualTo(4);
    }

    private static InterviewReport report(long id, long sessionId, String score) {
        OffsetDateTime now = OffsetDateTime.now();
        return new InterviewReport(
                id,
                sessionId,
                7L,
                "READY",
                score == null ? null : new BigDecimal(score),
                List.of(),
                "summary",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                0,
                null,
                now,
                now);
    }
}
