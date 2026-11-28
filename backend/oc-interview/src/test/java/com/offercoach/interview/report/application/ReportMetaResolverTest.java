package com.offercoach.interview.report.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.offercoach.interview.jd.application.port.JobDescriptionRepository;
import com.offercoach.interview.jd.domain.JobDescription;
import com.offercoach.interview.resume.application.port.ResumeRepository;
import com.offercoach.interview.resume.domain.Resume;
import com.offercoach.interview.session.application.port.InterviewSessionRepository;
import com.offercoach.interview.session.domain.InterviewSession;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 报告列表元信息必须是批量查询：逐份 resolve 时一页 20 份报告要发 61 条 SQL。
 *
 * <p>这里用"每个仓储只被调用一次"来钉住 N+1 不回归。</p>
 */
class ReportMetaResolverTest {

    private static final long USER_ID = 7L;

    private InterviewSessionRepository sessionRepository;
    private JobDescriptionRepository jdRepository;
    private ResumeRepository resumeRepository;
    private ReportMetaResolver resolver;

    @BeforeEach
    void setUp() {
        sessionRepository = mock(InterviewSessionRepository.class);
        jdRepository = mock(JobDescriptionRepository.class);
        resumeRepository = mock(ResumeRepository.class);
        resolver = new ReportMetaResolver(sessionRepository, jdRepository, resumeRepository);
    }

    @Test
    void resolvesWholePageWithThreeQueries() {
        when(sessionRepository.findByIdsOwned(USER_ID, Set.of(100L, 101L)))
                .thenReturn(List.of(session(100L, 11L, 21L, 5), session(101L, 12L, 22L, 4)));
        when(jdRepository.findOwnedByIds(USER_ID, Set.of(11L, 12L)))
                .thenReturn(List.of(jd(11L, "高级后端工程师", "某科技"), jd(12L, "数据工程师", "某数据")));
        when(resumeRepository.findOwnedByIds(USER_ID, Set.of(21L, 22L)))
                .thenReturn(List.of(resume(21L, "张三的简历"), resume(22L, null)));

        Map<Long, ReportMetaResolver.Meta> metas = resolver.resolveAll(USER_ID, Set.of(100L, 101L));

        assertThat(metas).hasSize(2);
        assertThat(metas.get(100L).title()).isEqualTo("张三的简历");
        assertThat(metas.get(100L).company()).isEqualTo("某科技");
        assertThat(metas.get(100L).role()).isEqualTo("高级后端工程师");
        assertThat(metas.get(100L).questionCount()).isEqualTo(5);
        // 没有简历标题时回退成「岗位 · 面试报告」
        assertThat(metas.get(101L).title()).isEqualTo("数据工程师 · 面试报告");

        // 关键断言：每类仓储只查一次，且不再逐条 findOwned
        verify(sessionRepository).findByIdsOwned(USER_ID, Set.of(100L, 101L));
        verify(jdRepository).findOwnedByIds(USER_ID, Set.of(11L, 12L));
        verify(resumeRepository).findOwnedByIds(USER_ID, Set.of(21L, 22L));
        verify(sessionRepository, never()).findAny(anyLong());
        verify(jdRepository, never()).findOwned(anyLong(), anyLong());
        verify(resumeRepository, never()).findOwned(anyLong(), anyLong());
    }

    @Test
    void returnsEmptyMapWithoutQueryingWhenNoIds() {
        assertThat(resolver.resolveAll(USER_ID, List.of())).isEmpty();
        verifyNoInteractions(sessionRepository, jdRepository, resumeRepository);
    }

    /**
     * 会话缺失（报告已被清理但列表缓存还在）时给占位值，不能让整页 500。
     */
    @Test
    void fallsBackWhenSessionMissing() {
        when(sessionRepository.findByIdsOwned(USER_ID, Set.of(100L))).thenReturn(List.of());
        when(sessionRepository.findByIdsOwned(USER_ID, Set.of(999L))).thenReturn(List.of());
        when(jdRepository.findOwnedByIds(anyLong(), any())).thenReturn(List.of());
        when(resumeRepository.findOwnedByIds(anyLong(), any())).thenReturn(List.of());

        Map<Long, ReportMetaResolver.Meta> metas = resolver.resolveAll(USER_ID, Set.of(100L));

        assertThat(metas.get(100L).title()).isEqualTo("模拟面试 · 面试报告");
        assertThat(metas.get(100L).company()).isEqualTo("目标公司");
        assertThat(metas.get(100L).questionCount()).isZero();
        assertThat(resolver.resolve(USER_ID, 999L).title()).isEqualTo("模拟面试 · 面试报告");
    }

    private static InterviewSession session(long id, long jdId, long resumeId, int questionCount) {
        return new InterviewSession(
                id,
                USER_ID,
                resumeId,
                jdId,
                "FINISHED",
                "{}",
                questionCount,
                questionCount,
                questionCount,
                null,
                null,
                "key-" + id,
                null,
                null,
                null,
                null);
    }

    private static JobDescription jd(long id, String title, String company) {
        return new JobDescription(
                id, USER_ID, title, company, "raw", "{}", BigDecimal.ZERO, null, "hash-" + id, null, "MANUAL", null);
    }

    private static Resume resume(long id, String title) {
        return new Resume(
                id,
                USER_ID,
                title,
                "FILE",
                "resumes/7/x.pdf",
                "x.pdf",
                "application/pdf",
                10L,
                "sha-" + id,
                null,
                "READY",
                null,
                "raw",
                "{}",
                "summary",
                null,
                false,
                null);
    }
}
