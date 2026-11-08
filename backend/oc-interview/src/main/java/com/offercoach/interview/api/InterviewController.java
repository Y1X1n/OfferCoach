package com.offercoach.interview.api;

import com.offercoach.common.error.BizException;
import com.offercoach.common.error.ErrorCode;
import com.offercoach.identity.security.CurrentUser;
import com.offercoach.interview.api.dto.PageResponse;
import com.offercoach.interview.api.dto.interview.AnswerDetailResponse;
import com.offercoach.interview.api.dto.interview.AnswerSubmitRequest;
import com.offercoach.interview.api.dto.interview.AnswerSubmitResponse;
import com.offercoach.interview.api.dto.interview.CreateSessionRequest;
import com.offercoach.interview.api.dto.interview.FeedbackResponse;
import com.offercoach.interview.api.dto.interview.QuestionItemResponse;
import com.offercoach.interview.api.dto.interview.QuestionsResponse;
import com.offercoach.interview.api.dto.interview.SessionCreatedResponse;
import com.offercoach.interview.api.dto.interview.SessionDetailResponse;
import com.offercoach.interview.api.dto.interview.SessionStartedResponse;
import com.offercoach.interview.api.dto.interview.SessionSummaryResponse;
import com.offercoach.interview.feedback.application.FeedbackService;
import com.offercoach.interview.feedback.domain.AnswerFeedback;
import com.offercoach.interview.session.application.AnswerService;
import com.offercoach.interview.session.application.InterviewSessionService;
import com.offercoach.interview.session.domain.SessionConfig;
import jakarta.validation.Valid;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 面试会话端点：创建、开始、查询、作答、暂停/恢复、放弃、结束与出题进度 SSE。
 */
@RestController
@RequestMapping("/api/v1/interviews")
public class InterviewController {

    private static final long EVENT_TIMEOUT_MS = 30_000L;
    private static final long EVENT_POLL_MS = 500L;

    private final InterviewSessionService sessionService;
    private final AnswerService answerService;
    private final FeedbackService feedbackService;
    private final CurrentUser currentUser;
    private final Executor sseExecutor;

    public InterviewController(
            InterviewSessionService sessionService,
            AnswerService answerService,
            FeedbackService feedbackService,
            CurrentUser currentUser,
            @Qualifier("sseExecutor") Executor sseExecutor) {
        this.sessionService = sessionService;
        this.answerService = answerService;
        this.feedbackService = feedbackService;
        this.currentUser = currentUser;
        this.sseExecutor = sseExecutor;
    }

    @PostMapping
    public ResponseEntity<SessionCreatedResponse> create(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateSessionRequest request) {
        InterviewSessionService.SessionCreated created = sessionService.create(
                currentUser.userId(),
                parseId(request.resumeId(), "resumeId"),
                parseId(request.jdId(), "jdId"),
                toConfig(request.config()),
                idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new SessionCreatedResponse(
                        Long.toString(created.id()),
                        created.status(),
                        created.questionCount(),
                        new SessionCreatedResponse.Quota(created.charged(), created.balance()),
                        timestamp(created.createdAt())));
    }

    @GetMapping
    public PageResponse<SessionSummaryResponse> list(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        InterviewSessionService.SessionPage result = sessionService.list(currentUser.userId(), page, size);
        List<SessionSummaryResponse> items = result.items().stream()
                .map(item -> new SessionSummaryResponse(
                        Long.toString(item.id()),
                        item.status(),
                        item.questionCount(),
                        item.answeredCount(),
                        item.currentSeq(),
                        item.totalScore(),
                        Long.toString(item.resumeId()),
                        Long.toString(item.jdId()),
                        timestamp(item.createdAt()),
                        timestamp(item.lastActiveAt())))
                .toList();
        return PageResponse.of(items, result.total(), result.page(), result.size());
    }

    @GetMapping("/{id}")
    public SessionDetailResponse detail(@PathVariable("id") String id) {
        InterviewSessionService.SessionView view = sessionService.detail(currentUser.userId(), parseId(id, "id"));
        return toDetail(view);
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<SessionStartedResponse> start(@PathVariable("id") String id) {
        InterviewSessionService.Started started = sessionService.start(currentUser.userId(), parseId(id, "id"));
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new SessionStartedResponse(
                        Long.toString(started.id()), started.status(), started.estimatedReadyInMs()));
    }

    @GetMapping("/{id}/questions")
    public QuestionsResponse questions(@PathVariable("id") String id) {
        List<QuestionItemResponse> items = sessionService.questions(currentUser.userId(), parseId(id, "id")).stream()
                .map(InterviewController::toQuestion)
                .toList();
        return new QuestionsResponse(items);
    }

    @PostMapping("/{id}/questions/{questionId}/skip")
    public ResponseEntity<Void> skip(@PathVariable("id") String id, @PathVariable String questionId) {
        sessionService.skip(currentUser.userId(), parseId(id, "id"), parseId(questionId, "questionId"));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/pause")
    public ResponseEntity<Void> pause(@PathVariable("id") String id) {
        sessionService.pause(currentUser.userId(), parseId(id, "id"));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/resume")
    public ResponseEntity<Void> resume(@PathVariable("id") String id) {
        sessionService.resume(currentUser.userId(), parseId(id, "id"));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/abandon")
    public ResponseEntity<Void> abandon(@PathVariable("id") String id) {
        sessionService.abandon(currentUser.userId(), parseId(id, "id"));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/answers")
    public ResponseEntity<AnswerSubmitResponse> answer(
            @PathVariable("id") String id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody AnswerSubmitRequest request) {
        AnswerService.Submitted submitted = answerService.submit(
                currentUser.userId(),
                parseId(id, "id"),
                parseId(request.questionId(), "questionId"),
                request.answerText(),
                request.durationMs(),
                request.inputMode(),
                idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new AnswerSubmitResponse(
                        Long.toString(submitted.answerId()),
                        submitted.status(),
                        new AnswerSubmitResponse.StreamInfo(
                                "/api/v1/ai/feedback/stream",
                                Long.toString(submitted.answerId()),
                                submitted.token(),
                                submitted.expiresIn())));
    }

    @GetMapping("/{id}/answers/{answerId}")
    public AnswerDetailResponse answerDetail(@PathVariable("id") String id, @PathVariable String answerId) {
        AnswerService.AnswerView view =
                answerService.detail(currentUser.userId(), parseId(id, "id"), parseId(answerId, "answerId"));
        return new AnswerDetailResponse(
                Long.toString(view.id()),
                Long.toString(view.questionId()),
                Long.toString(view.sessionId()),
                view.inputMode(),
                view.answerText(),
                view.durationMs(),
                view.status(),
                timestamp(view.submittedAt()));
    }

    @GetMapping("/{id}/answers/{answerId}/feedback")
    public FeedbackResponse feedback(@PathVariable("id") String id, @PathVariable String answerId) {
        long userId = currentUser.userId();
        long sessionId = parseId(id, "id");
        long answerIdValue = parseId(answerId, "answerId");
        answerService.loadOwned(userId, sessionId, answerIdValue);
        return toFeedback(feedbackService.latest(userId, sessionId, answerIdValue));
    }

    @PostMapping("/{id}/answers/{answerId}/feedback")
    public FeedbackResponse generateFeedback(@PathVariable("id") String id, @PathVariable String answerId) {
        long userId = currentUser.userId();
        long sessionId = parseId(id, "id");
        long answerIdValue = parseId(answerId, "answerId");
        answerService.loadOwned(userId, sessionId, answerIdValue);
        return toFeedback(feedbackService.prepare(userId, answerIdValue).feedback());
    }

    @PostMapping("/{id}/finish")
    public ResponseEntity<Map<String, Object>> finish(@PathVariable("id") String id) {
        InterviewSessionService.Finished finished = sessionService.finish(currentUser.userId(), parseId(id, "id"));
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of(
                        "reportId", Long.toString(finished.reportId()),
                        "status", finished.status(),
                        "estimatedReadyInMs", finished.estimatedReadyInMs()));
    }

    /**
     * 出题进度 SSE：轮询会话状态直到 READY/FAILED，客户端也可改为普通轮询。
     */
    @GetMapping("/{id}/events")
    public SseEmitter events(@PathVariable("id") String id) {
        long userId = currentUser.userId();
        long sessionId = parseId(id, "id");
        SseEmitter emitter = new SseEmitter(EVENT_TIMEOUT_MS);
        sseExecutor.execute(() -> pumpEvents(emitter, userId, sessionId));
        return emitter;
    }

    private void pumpEvents(SseEmitter emitter, long userId, long sessionId) {
        try {
            long deadline = System.currentTimeMillis() + EVENT_TIMEOUT_MS - 1000;
            int sentReady = -1;
            while (System.currentTimeMillis() < deadline) {
                InterviewSessionService.SessionView view = sessionService.detail(userId, sessionId);
                emitter.send(SseEmitter.event()
                        .name("status")
                        .data(Map.of(
                                "status", view.status(),
                                "answered", view.answered(),
                                "total", view.total())));
                if ("READY".equals(view.status()) || "IN_PROGRESS".equals(view.status())) {
                    if (sentReady < 0) {
                        for (QuestionItemResponse question : sessionService.questions(userId, sessionId).stream()
                                .map(InterviewController::toQuestion)
                                .toList()) {
                            emitter.send(SseEmitter.event()
                                    .name("question_ready")
                                    .data(Map.of(
                                            "seq", question.seq(),
                                            "questionId", question.id(),
                                            "dimension", question.dimension(),
                                            "difficulty", question.difficulty())));
                        }
                        sentReady = 1;
                    }
                    if ("READY".equals(view.status())) {
                        emitter.send(
                                SseEmitter.event().name("status").data(Map.of("status", "READY", "progress", 100)));
                        emitter.complete();
                        return;
                    }
                }
                if ("FAILED".equals(view.status()) || "ABANDONED".equals(view.status())) {
                    emitter.send(SseEmitter.event()
                            .name("failed")
                            .data(Map.of("code", "QUESTION_GEN_FAILED", "status", view.status())));
                    emitter.complete();
                    return;
                }
                Thread.sleep(EVENT_POLL_MS);
            }
            emitter.complete();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            emitter.complete();
        } catch (IOException exception) {
            emitter.completeWithError(exception);
        } catch (RuntimeException exception) {
            try {
                emitter.send(SseEmitter.event().name("failed").data(Map.of("code", "STREAM_ERROR", "retryable", true)));
                emitter.complete();
            } catch (IOException ignored) {
                emitter.completeWithError(ignored);
            }
        }
    }

    private static SessionDetailResponse toDetail(InterviewSessionService.SessionView view) {
        SessionConfig config = view.config();
        return new SessionDetailResponse(
                Long.toString(view.id()),
                view.status(),
                new SessionDetailResponse.ConfigView(
                        config.questionCount(),
                        config.difficulty(),
                        config.interviewerStyle(),
                        config.answerMode(),
                        config.timeLimitSec(),
                        config.language()),
                new SessionDetailResponse.Progress(view.answered(), view.skipped(), view.total()),
                view.currentSeq(),
                view.totalScore(),
                view.jdTitle(),
                view.jdCompany(),
                view.reportId(),
                timestamp(view.startedAt()),
                timestamp(view.lastActiveAt()),
                timestamp(view.createdAt()));
    }

    private static QuestionItemResponse toQuestion(InterviewSessionService.QuestionView view) {
        return new QuestionItemResponse(
                Long.toString(view.id()),
                view.seq(),
                view.dimension(),
                view.difficulty(),
                view.text(),
                view.source(),
                view.referencePoints(),
                view.status(),
                view.whyThisQuestion(),
                view.answerText(),
                view.durationMs(),
                view.answerId());
    }

    private static FeedbackResponse toFeedback(AnswerFeedback feedback) {
        return new FeedbackResponse(
                Long.toString(feedback.id()),
                feedback.version(),
                Long.toString(feedback.answerId()),
                feedback.overallScore(),
                feedback.dimensions(),
                feedback.strengths(),
                feedback.improvements(),
                feedback.evidence().stream()
                        .map(item -> new FeedbackResponse.Evidence(item.quote(), item.reason()))
                        .toList(),
                feedback.rewrittenAnswer(),
                feedback.model(),
                feedback.degraded(),
                timestamp(feedback.createdAt()));
    }

    private static SessionConfig toConfig(CreateSessionRequest.Config config) {
        int questionCount = config.questionCount() == null ? 8 : config.questionCount();
        int timeLimit = config.timeLimitSec() == null ? 180 : config.timeLimitSec();
        return new SessionConfig(
                questionCount,
                config.difficulty() == null ? "MIXED" : config.difficulty(),
                config.interviewerStyle() == null ? "STANDARD" : config.interviewerStyle(),
                config.answerMode() == null ? "TEXT" : config.answerMode(),
                timeLimit,
                config.language() == null ? "zh-CN" : config.language());
    }

    private static String timestamp(OffsetDateTime value) {
        return value == null ? null : value.toInstant().toString();
    }

    private static long parseId(String id, String field) {
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException exception) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of(field, "非数字 ID")));
        }
    }
}
