package com.offercoach.interview.api;

import com.offercoach.common.error.BizException;
import com.offercoach.common.error.ErrorCode;
import com.offercoach.identity.security.CurrentUser;
import com.offercoach.interview.api.dto.PageResponse;
import com.offercoach.interview.api.dto.report.ReportDetailResponse;
import com.offercoach.interview.api.dto.report.ReportStartedResponse;
import com.offercoach.interview.api.dto.report.ReportSummaryResponse;
import com.offercoach.interview.feedback.domain.AnswerFeedback;
import com.offercoach.interview.report.application.ReportBreakdownResolver;
import com.offercoach.interview.report.application.ReportGenerationWorker;
import com.offercoach.interview.report.application.ReportMetaResolver;
import com.offercoach.interview.report.application.ReportService;
import com.offercoach.interview.report.domain.InterviewReport;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 面试报告端点：列表、详情与失败重试。
 */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService reportService;
    private final ReportGenerationWorker reportGenerationWorker;
    private final ReportMetaResolver reportMetaResolver;
    private final ReportBreakdownResolver reportBreakdownResolver;
    private final CurrentUser currentUser;

    public ReportController(
            ReportService reportService,
            ReportGenerationWorker reportGenerationWorker,
            ReportMetaResolver reportMetaResolver,
            ReportBreakdownResolver reportBreakdownResolver,
            CurrentUser currentUser) {
        this.reportService = reportService;
        this.reportGenerationWorker = reportGenerationWorker;
        this.reportMetaResolver = reportMetaResolver;
        this.reportBreakdownResolver = reportBreakdownResolver;
        this.currentUser = currentUser;
    }

    @GetMapping
    public PageResponse<ReportSummaryResponse> list(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        ReportService.ReportPage result = reportService.list(currentUser.userId(), page, size);
        long userId = currentUser.userId();
        // 批量解析元信息：逐份 resolve 时每份报告要查会话/JD/简历三次（20 份 = 61 条查询）。
        Map<Long, ReportMetaResolver.Meta> metas = reportMetaResolver.resolveAll(
                userId, result.items().stream().map(InterviewReport::sessionId).toList());
        List<ReportSummaryResponse> items = result.items().stream()
                .map(item -> toSummary(item, metas.getOrDefault(item.sessionId(), ReportMetaResolver.unknown())))
                .toList();
        return PageResponse.of(items, result.total(), result.page(), result.size());
    }

    @GetMapping("/{id}")
    public ReportDetailResponse detail(@PathVariable("id") String id) {
        long userId = currentUser.userId();
        return toDetail(userId, reportService.detail(userId, parseId(id)));
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<ReportStartedResponse> retry(@PathVariable("id") String id) {
        long reportId = parseId(id);
        reportService.retry(currentUser.userId(), reportId);
        reportGenerationWorker.kick(reportId);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new ReportStartedResponse(Long.toString(reportId), "GENERATING"));
    }

    private ReportSummaryResponse toSummary(InterviewReport report, ReportMetaResolver.Meta meta) {
        return new ReportSummaryResponse(
                Long.toString(report.id()),
                Long.toString(report.sessionId()),
                report.status(),
                report.overallScore(),
                report.summary(),
                meta.title(),
                meta.company(),
                meta.role(),
                meta.questionCount(),
                timestamp(report.generatedAt()),
                timestamp(report.createdAt()));
    }

    private ReportDetailResponse toDetail(long userId, InterviewReport report) {
        ReportMetaResolver.Meta meta = reportMetaResolver.resolve(userId, report.sessionId());
        // 逐题素材按会话一次取齐：逐题查时 20 题要 60 条 SQL。
        ReportBreakdownResolver.Sources sources = reportBreakdownResolver.resolve(userId, report.sessionId());
        return new ReportDetailResponse(
                Long.toString(report.id()),
                Long.toString(report.sessionId()),
                meta.title(),
                meta.company(),
                meta.role(),
                meta.questionCount(),
                report.status(),
                report.overallScore(),
                report.radar().stream()
                        .map(item -> new ReportDetailResponse.RadarItem(item.dimension(), item.label(), item.score()))
                        .toList(),
                report.summary(),
                report.highlights(),
                report.risks(),
                report.improvementPlan().stream()
                        .map(item -> new ReportDetailResponse.ImprovementItem(
                                item.priority(), item.action(), item.reason(), item.resource()))
                        .toList(),
                report.questionBreakdown().stream()
                        .map(item -> toBreakdown(sources, item))
                        .toList(),
                timestamp(report.generatedAt()),
                timestamp(report.createdAt()));
    }

    /**
     * 逐题拆解补充回答原文与反馈证据：数据来自回答与反馈表，旧报告同样适用。
     */
    private ReportDetailResponse.BreakdownItem toBreakdown(
            ReportBreakdownResolver.Sources sources, InterviewReport.BreakdownItem item) {
        AnswerFeedback feedback = sources.feedback(item.answerId());
        ReportDetailResponse.FeedbackView feedbackView = feedback == null
                ? null
                : new ReportDetailResponse.FeedbackView(
                        feedback.overallScore(),
                        feedback.dimensions(),
                        feedback.strengths(),
                        feedback.improvements(),
                        feedback.evidence().stream()
                                .map(evidence ->
                                        new ReportDetailResponse.EvidenceView(evidence.quote(), evidence.reason()))
                                .toList(),
                        feedback.rewrittenAnswer());
        return new ReportDetailResponse.BreakdownItem(
                Long.toString(item.questionId()),
                Long.toString(item.answerId()),
                Long.toString(item.feedbackId()),
                item.seq(),
                item.score(),
                item.dimension(),
                item.summary(),
                sources.questionText(item.questionId()),
                sources.answerText(item.answerId()),
                feedbackView);
    }

    private static String timestamp(OffsetDateTime value) {
        return value == null ? null : value.toInstant().toString();
    }

    private static long parseId(String id) {
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException exception) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of("id", "非数字 ID")));
        }
    }
}
