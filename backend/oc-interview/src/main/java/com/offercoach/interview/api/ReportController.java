package com.offercoach.interview.api;

import com.offercoach.common.error.BizException;
import com.offercoach.common.error.ErrorCode;
import com.offercoach.identity.security.CurrentUser;
import com.offercoach.interview.api.dto.PageResponse;
import com.offercoach.interview.api.dto.report.ReportDetailResponse;
import com.offercoach.interview.api.dto.report.ReportStartedResponse;
import com.offercoach.interview.api.dto.report.ReportSummaryResponse;
import com.offercoach.interview.feedback.application.port.AnswerFeedbackRepository;
import com.offercoach.interview.feedback.domain.AnswerFeedback;
import com.offercoach.interview.jd.application.port.JobDescriptionRepository;
import com.offercoach.interview.jd.domain.JobDescription;
import com.offercoach.interview.report.application.ReportGenerationWorker;
import com.offercoach.interview.report.application.ReportService;
import com.offercoach.interview.report.domain.InterviewReport;
import com.offercoach.interview.resume.application.port.ResumeRepository;
import com.offercoach.interview.resume.domain.Resume;
import com.offercoach.interview.session.application.port.InterviewAnswerRepository;
import com.offercoach.interview.session.application.port.InterviewQuestionRepository;
import com.offercoach.interview.session.application.port.InterviewSessionRepository;
import com.offercoach.interview.session.domain.InterviewAnswer;
import com.offercoach.interview.session.domain.InterviewQuestion;
import com.offercoach.interview.session.domain.InterviewSession;
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
    private final InterviewSessionRepository sessionRepository;
    private final InterviewQuestionRepository questionRepository;
    private final InterviewAnswerRepository answerRepository;
    private final AnswerFeedbackRepository feedbackRepository;
    private final JobDescriptionRepository jdRepository;
    private final ResumeRepository resumeRepository;
    private final CurrentUser currentUser;

    public ReportController(
            ReportService reportService,
            ReportGenerationWorker reportGenerationWorker,
            InterviewSessionRepository sessionRepository,
            InterviewQuestionRepository questionRepository,
            InterviewAnswerRepository answerRepository,
            AnswerFeedbackRepository feedbackRepository,
            JobDescriptionRepository jdRepository,
            ResumeRepository resumeRepository,
            CurrentUser currentUser) {
        this.reportService = reportService;
        this.reportGenerationWorker = reportGenerationWorker;
        this.sessionRepository = sessionRepository;
        this.questionRepository = questionRepository;
        this.answerRepository = answerRepository;
        this.feedbackRepository = feedbackRepository;
        this.jdRepository = jdRepository;
        this.resumeRepository = resumeRepository;
        this.currentUser = currentUser;
    }

    @GetMapping
    public PageResponse<ReportSummaryResponse> list(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        ReportService.ReportPage result = reportService.list(currentUser.userId(), page, size);
        long userId = currentUser.userId();
        List<ReportSummaryResponse> items =
                result.items().stream().map(item -> toSummary(userId, item)).toList();
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

    private ReportSummaryResponse toSummary(long userId, InterviewReport report) {
        ReportMeta meta = meta(userId, report);
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
        ReportMeta meta = meta(userId, report);
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
                        .map(item -> toBreakdown(userId, report.sessionId(), item))
                        .toList(),
                timestamp(report.generatedAt()),
                timestamp(report.createdAt()));
    }

    /**
     * 逐题拆解补充回答原文与反馈证据：数据来自回答与反馈表，旧报告同样适用。
     */
    private ReportDetailResponse.BreakdownItem toBreakdown(
            long userId, long sessionId, InterviewReport.BreakdownItem item) {
        InterviewQuestion question =
                questionRepository.findOwned(sessionId, item.questionId()).orElse(null);
        InterviewAnswer answer =
                answerRepository.findOwned(userId, sessionId, item.answerId()).orElse(null);
        AnswerFeedback feedback =
                feedbackRepository.findLatestByAnswer(userId, item.answerId()).orElse(null);
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
                question == null ? null : question.text(),
                answer == null ? null : answer.answerText(),
                feedbackView);
    }

    private ReportMeta meta(long userId, InterviewReport report) {
        InterviewSession session = sessionRepository.findAny(report.sessionId()).orElse(null);
        JobDescription jd = session == null
                ? null
                : jdRepository.findOwned(userId, session.jdId()).orElse(null);
        Resume resume = session == null
                ? null
                : resumeRepository.findOwned(userId, session.resumeId()).orElse(null);
        String jdTitle = jd == null ? null : jd.title();
        String company = jd == null ? null : jd.company();
        String role = jdTitle == null || jdTitle.isBlank() ? "模拟面试" : jdTitle;
        String title =
                resume != null && resume.title() != null && !resume.title().isBlank()
                        ? resume.title()
                        : role + " · 面试报告";
        return new ReportMeta(
                title,
                company == null || company.isBlank() ? "目标公司" : company,
                role,
                session == null ? 0 : session.questionCount());
    }

    private record ReportMeta(String title, String company, String role, int questionCount) {}

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
