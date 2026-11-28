package com.offercoach.interview.api;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.offercoach.identity.security.CurrentUser;
import com.offercoach.interview.feedback.domain.AnswerFeedback;
import com.offercoach.interview.report.application.ReportBreakdownResolver;
import com.offercoach.interview.report.application.ReportGenerationWorker;
import com.offercoach.interview.report.application.ReportMetaResolver;
import com.offercoach.interview.report.application.ReportService;
import com.offercoach.interview.report.domain.InterviewReport;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 报告详情：逐题素材只在真有 breakdown 时批量取。
 *
 * <p>生成中的报告 {@code questionBreakdown} 是空的，而前端每 2-15 秒轮询一次详情——
 * 这时如果还去按会话扫题目/回答/反馈三张表，每次轮询都是白查（还会把整段回答原文读出来丢掉）。</p>
 */
class ReportControllerDetailTest {

    private static final long USER_ID = 7L;
    private static final long REPORT_ID = 3L;
    private static final long SESSION_ID = 100L;

    private ReportService reportService;
    private ReportBreakdownResolver breakdownResolver;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        reportService = mock(ReportService.class);
        ReportGenerationWorker worker = mock(ReportGenerationWorker.class);
        ReportMetaResolver metaResolver = mock(ReportMetaResolver.class);
        breakdownResolver = mock(ReportBreakdownResolver.class);
        CurrentUser currentUser = mock(CurrentUser.class);
        when(currentUser.userId()).thenReturn(USER_ID);
        when(metaResolver.resolve(anyLong(), anyLong()))
                .thenReturn(new ReportMetaResolver.Meta("简历标题", "某科技", "后端工程师", 2));

        mvc = MockMvcBuilders.standaloneSetup(
                        new ReportController(reportService, worker, metaResolver, breakdownResolver, currentUser))
                .build();
    }

    @Test
    void generatingReportDoesNotLoadBreakdownSources() throws Exception {
        when(reportService.detail(USER_ID, REPORT_ID)).thenReturn(report("GENERATING", List.of()));

        mvc.perform(get("/api/v1/reports/{id}", REPORT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("GENERATING"));

        verify(breakdownResolver, never()).resolve(anyLong(), anyLong());
    }

    @Test
    void readyReportLoadsBreakdownOnceAndFillsTexts() throws Exception {
        when(reportService.detail(USER_ID, REPORT_ID)).thenReturn(report("READY", List.of(breakdown(11L, 21L, 31L))));
        when(breakdownResolver.resolve(USER_ID, SESSION_ID))
                .thenReturn(new ReportBreakdownResolver.Sources(
                        Map.of(11L, "介绍一下你的项目"), Map.of(21L, "做了个订单系统"), Map.of(21L, feedback(31L, 21L))));

        mvc.perform(get("/api/v1/reports/{id}", REPORT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questionBreakdown[0].question").value("介绍一下你的项目"))
                .andExpect(jsonPath("$.questionBreakdown[0].answer").value("做了个订单系统"))
                .andExpect(
                        jsonPath("$.questionBreakdown[0].feedback.overallScore").value(7.5));

        verify(breakdownResolver).resolve(USER_ID, SESSION_ID);
    }

    private static InterviewReport report(String status, List<InterviewReport.BreakdownItem> breakdown) {
        OffsetDateTime now = OffsetDateTime.now();
        return new InterviewReport(
                REPORT_ID,
                SESSION_ID,
                USER_ID,
                status,
                new BigDecimal("80.0"),
                List.of(),
                "summary",
                List.of(),
                List.of(),
                List.of(),
                breakdown,
                1,
                null,
                now,
                now);
    }

    private static InterviewReport.BreakdownItem breakdown(long questionId, long answerId, long feedbackId) {
        return new InterviewReport.BreakdownItem(questionId, answerId, feedbackId, 1, 8.0, "TECH", "答得不错");
    }

    private static AnswerFeedback feedback(long id, long answerId) {
        return new AnswerFeedback(
                id,
                answerId,
                SESSION_ID,
                USER_ID,
                1,
                "req-" + id,
                new BigDecimal("7.5"),
                Map.of(),
                List.of("结构清晰"),
                List.of(),
                List.of(),
                "改写稿",
                "model",
                "provider",
                false,
                OffsetDateTime.now());
    }
}
