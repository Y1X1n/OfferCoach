package com.offercoach.interview.feedback.application;

import com.offercoach.common.error.BizException;
import com.offercoach.common.error.ErrorCode;
import com.offercoach.identity.application.quota.QuotaService;
import com.offercoach.interview.ai.AiCallLogger;
import com.offercoach.interview.feedback.application.port.AnswerFeedbackRepository;
import com.offercoach.interview.feedback.domain.AnswerFeedback;
import com.offercoach.interview.jd.application.port.JobDescriptionRepository;
import com.offercoach.interview.jd.domain.JobDescription;
import com.offercoach.interview.resume.application.port.ResumeRepository;
import com.offercoach.interview.resume.domain.Resume;
import com.offercoach.interview.session.application.QuestionContextBuilder;
import com.offercoach.interview.session.application.port.InterviewAnswerRepository;
import com.offercoach.interview.session.application.port.InterviewQuestionRepository;
import com.offercoach.interview.session.application.port.InterviewSessionRepository;
import com.offercoach.interview.session.domain.InterviewAnswer;
import com.offercoach.interview.session.domain.InterviewQuestion;
import com.offercoach.interview.session.domain.InterviewSession;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 反馈评分：先计算并幂等落库，再交给流式层输出；分数不依赖模型。
 */
@Service
public class FeedbackService {

    private static final Logger log = LoggerFactory.getLogger(FeedbackService.class);
    private static final String REQUEST_ID_PREFIX = "fb:";
    private static final String PROVIDER = "heuristic";
    private static final String MODEL = "rules-v2";
    private static final String PRO_PLAN = "PRO";

    private final InterviewSessionRepository sessionRepository;
    private final InterviewQuestionRepository questionRepository;
    private final InterviewAnswerRepository answerRepository;
    private final AnswerFeedbackRepository feedbackRepository;
    private final ResumeRepository resumeRepository;
    private final JobDescriptionRepository jdRepository;
    private final QuestionContextBuilder contextBuilder;
    private final QuotaService quotaService;
    private final AiCallLogger aiCallLogger;

    public FeedbackService(
            InterviewSessionRepository sessionRepository,
            InterviewQuestionRepository questionRepository,
            InterviewAnswerRepository answerRepository,
            AnswerFeedbackRepository feedbackRepository,
            ResumeRepository resumeRepository,
            JobDescriptionRepository jdRepository,
            QuestionContextBuilder contextBuilder,
            QuotaService quotaService,
            AiCallLogger aiCallLogger) {
        this.sessionRepository = sessionRepository;
        this.questionRepository = questionRepository;
        this.answerRepository = answerRepository;
        this.feedbackRepository = feedbackRepository;
        this.resumeRepository = resumeRepository;
        this.jdRepository = jdRepository;
        this.contextBuilder = contextBuilder;
        this.quotaService = quotaService;
        this.aiCallLogger = aiCallLogger;
    }

    /**
     * 计算并幂等保存反馈；已有 requestId 时直接返回已有结果。
     */
    @Transactional
    public Prepared prepare(long userId, long answerId) {
        long startedAt = System.nanoTime();
        InterviewAnswer answer = answerRepository
                .findOwnedByAnswer(userId, answerId)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("answerId", answerId)));
        InterviewSession session = sessionRepository
                .findOwned(userId, answer.sessionId())
                .orElseThrow(
                        () -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("sessionId", answer.sessionId())));
        InterviewQuestion question = questionRepository
                .findOwned(session.id(), answer.questionId())
                .orElseThrow(() ->
                        new BizException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("questionId", answer.questionId())));

        String requestId = REQUEST_ID_PREFIX + answerId;
        Optional<AnswerFeedback> existing = feedbackRepository.findByRequestId(requestId);
        if (existing.isPresent()) {
            return new Prepared(existing.get(), question, answer, session);
        }

        boolean proPlan = isPro(userId);
        List<String> jdKeywords = jdKeywords(userId, session);
        FeedbackScorer.Result scored = FeedbackScorer.score(new FeedbackScorer.Input(
                answer.answerText(),
                question.text(),
                question.referencePoints(),
                jdKeywords,
                proPlan,
                question.dimension()));
        AnswerFeedback feedback = new AnswerFeedback(
                0,
                answer.id(),
                session.id(),
                userId,
                1,
                requestId,
                scored.overallScore(),
                scored.dimensions(),
                scored.strengths(),
                scored.improvements(),
                scored.evidence(),
                scored.rewrittenAnswer(),
                MODEL,
                PROVIDER,
                true,
                OffsetDateTime.now(ZoneOffset.UTC));
        int inserted = feedbackRepository.insertIfAbsent(feedback);
        AnswerFeedback saved = inserted > 0
                ? feedback
                : feedbackRepository
                        .findByRequestId(requestId)
                        .orElseThrow(() -> new BizException(ErrorCode.INTERNAL_ERROR));
        answerRepository.markScored(answer.id());
        if (inserted > 0) {
            aiCallLogger.log(
                    userId,
                    "FEEDBACK",
                    PROVIDER,
                    MODEL,
                    "DEGRADED",
                    0,
                    0,
                    null,
                    (System.nanoTime() - startedAt) / 1_000_000,
                    null);
        }
        log.info("评分已落库：answerId={} overall={}", answerId, saved.overallScore());
        return new Prepared(saved, question, answer, session);
    }

    public AnswerFeedback latest(long userId, long sessionId, long answerId) {
        return feedbackRepository
                .findLatestByAnswer(userId, answerId)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("answerId", answerId)));
    }

    public String narrative(Prepared prepared) {
        FeedbackScorer.Result scored = FeedbackScorer.score(new FeedbackScorer.Input(
                prepared.answer().answerText(),
                prepared.question().text(),
                prepared.question().referencePoints(),
                List.of(),
                prepared.feedback().rewrittenAnswer() != null,
                prepared.question().dimension()));
        return scored.narrative();
    }

    private boolean isPro(long userId) {
        try {
            return PRO_PLAN.equalsIgnoreCase(quotaService.snapshot(userId).planCode());
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private List<String> jdKeywords(long userId, InterviewSession session) {
        try {
            Optional<Resume> resume = resumeRepository.findOwned(userId, session.resumeId());
            Optional<JobDescription> jd = jdRepository.findOwned(userId, session.jdId());
            if (resume.isEmpty() || jd.isEmpty()) {
                return List.of();
            }
            return contextBuilder.build(resume.get(), jd.get()).keywords();
        } catch (RuntimeException exception) {
            return List.of();
        }
    }

    public record Prepared(
            AnswerFeedback feedback, InterviewQuestion question, InterviewAnswer answer, InterviewSession session) {}
}
