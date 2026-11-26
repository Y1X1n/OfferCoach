package com.offercoach.interview.dashboard.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.offercoach.identity.application.quota.QuotaService;
import com.offercoach.identity.application.quota.QuotaSnapshot;
import com.offercoach.interview.jd.application.port.JobDescriptionRepository;
import com.offercoach.interview.report.application.ReportMetaResolver;
import com.offercoach.interview.report.application.port.InterviewReportRepository;
import com.offercoach.interview.resume.application.port.ResumeRepository;
import com.offercoach.interview.session.application.port.InterviewSessionRepository;
import java.lang.reflect.Method;
import java.time.LocalDate;
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

        DashboardService service = new DashboardService(
                quotaService, resumeRepository, jdRepository, sessionRepository, reportRepository, metaResolver);

        DashboardService.DashboardView view = service.load(7L);

        assertThat(view.quota().interviewCredits()).isEqualTo(3);
        assertThat(view.resumeReady()).isTrue();
        assertThat(view.jdCount()).isEqualTo(2L);
        assertThat(view.activeSession()).isNull();
        assertThat(view.recentReports()).isEmpty();
    }
}
