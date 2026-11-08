package com.offercoach.interview.session.application;

import com.offercoach.common.cache.RedisKeys;
import com.offercoach.common.error.BizException;
import com.offercoach.common.error.ErrorCode;
import com.offercoach.interview.config.InterviewProperties;
import com.offercoach.interview.session.application.port.InterviewAnswerRepository;
import com.offercoach.interview.session.application.port.InterviewQuestionRepository;
import com.offercoach.interview.session.application.port.InterviewSessionRepository;
import com.offercoach.interview.session.domain.InterviewAnswer;
import com.offercoach.interview.session.domain.InterviewQuestion;
import com.offercoach.interview.session.domain.InterviewSession;
import com.offercoach.interview.session.domain.SessionStatus;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 回答提交：校验会话与题目状态，落库后签发 5 分钟评分流令牌。
 */
@Service
public class AnswerService {

    private static final int STREAM_TOKEN_BYTES = 32;
    private static final long STREAM_TOKEN_TTL_SECONDS = 300L;
    private static final String INPUT_MODE_TEXT = "TEXT";
    private static final int MAX_IDEMPOTENCY_KEY = 128;

    private final InterviewSessionRepository sessionRepository;
    private final InterviewQuestionRepository questionRepository;
    private final InterviewAnswerRepository answerRepository;
    private final InterviewProperties properties;
    private final StringRedisTemplate redisTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final SecureRandom secureRandom = new SecureRandom();

    public AnswerService(
            InterviewSessionRepository sessionRepository,
            InterviewQuestionRepository questionRepository,
            InterviewAnswerRepository answerRepository,
            InterviewProperties properties,
            StringRedisTemplate redisTemplate,
            ApplicationEventPublisher eventPublisher) {
        this.sessionRepository = sessionRepository;
        this.questionRepository = questionRepository;
        this.answerRepository = answerRepository;
        this.properties = properties;
        this.redisTemplate = redisTemplate;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 提交回答；同一题重复提交返回 DUPLICATE_ANSWER 并指向已有回答。
     */
    @Transactional
    public Submitted submit(
            long userId,
            long sessionId,
            long questionId,
            String answerText,
            Integer durationMs,
            String inputMode,
            String idempotencyKey) {
        InterviewSession session = sessionRepository
                .findOwned(userId, sessionId)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("sessionId", sessionId)));
        if (!SessionStatus.IN_PROGRESS.equals(session.status())) {
            throw new BizException(ErrorCode.STATE_CONFLICT, Map.of("status", session.status()));
        }
        InterviewQuestion question = questionRepository
                .findOwned(sessionId, questionId)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("questionId", questionId)));
        if (question.seq() > session.currentSeq()) {
            throw new BizException(ErrorCode.STATE_CONFLICT, Map.of("reason", "NOT_CURRENT_QUESTION"));
        }
        Optional<InterviewAnswer> existing = answerRepository.findByQuestionId(questionId);
        if (existing.isPresent()) {
            throw new BizException(
                    ErrorCode.DUPLICATE_ANSWER,
                    Map.of("answerId", Long.toString(existing.get().id())));
        }
        String text = answerText == null ? "" : answerText.strip();
        if (text.length() < properties.getMinAnswerLength() || text.length() > properties.getMaxAnswerLength()) {
            throw new BizException(
                    ErrorCode.VALIDATION_FAILED,
                    Map.of(
                            "fields",
                            Map.of(
                                    "answerText",
                                    "回答长度需为 " + properties.getMinAnswerLength() + "-"
                                            + properties.getMaxAnswerLength())));
        }
        String mode = inputMode == null || inputMode.isBlank()
                ? INPUT_MODE_TEXT
                : inputMode.strip().toUpperCase();
        if (!INPUT_MODE_TEXT.equals(mode)) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of("inputMode", "暂仅支持文字作答")));
        }
        if (idempotencyKey != null && idempotencyKey.length() > MAX_IDEMPOTENCY_KEY) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of("Idempotency-Key", "过长")));
        }

        long answerId;
        try {
            answerId = answerRepository.insert(
                    new InterviewAnswer(0, sessionId, questionId, userId, mode, text, durationMs, "SCORING", null));
        } catch (DuplicateKeyException exception) {
            Optional<InterviewAnswer> raced = answerRepository.findByQuestionId(questionId);
            if (raced.isPresent()) {
                throw new BizException(
                        ErrorCode.DUPLICATE_ANSWER,
                        Map.of("answerId", Long.toString(raced.get().id())));
            }
            throw new BizException(ErrorCode.STATE_CONFLICT);
        }
        if (!questionRepository.markAnswered(questionId)) {
            throw new BizException(ErrorCode.STATE_CONFLICT, Map.of("reason", "QUESTION_CLOSED"));
        }
        sessionRepository.advanceAfterAnswer(sessionId);
        questionRepository.markAsked(sessionId, Math.min(session.currentSeq() + 1, session.questionCount()));

        String token = newToken();
        try {
            redisTemplate
                    .opsForValue()
                    .set(
                            RedisKeys.aiStreamToken(token),
                            userId + ":" + answerId,
                            Duration.ofSeconds(STREAM_TOKEN_TTL_SECONDS));
        } catch (RuntimeException exception) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, Map.of("reason", "STREAM_TOKEN_UNAVAILABLE"));
        }
        eventPublisher.publishEvent(new AnswerSubmitted(userId, sessionId, answerId));
        return new Submitted(answerId, "SCORING", token, STREAM_TOKEN_TTL_SECONDS);
    }

    public AnswerView detail(long userId, long sessionId, long answerId) {
        InterviewAnswer answer = loadOwned(userId, sessionId, answerId);
        return new AnswerView(
                answer.id(),
                answer.sessionId(),
                answer.questionId(),
                answer.inputMode(),
                answer.answerText(),
                answer.durationMs(),
                answer.status(),
                answer.submittedAt());
    }

    public InterviewAnswer loadOwned(long userId, long sessionId, long answerId) {
        return answerRepository
                .findOwned(userId, sessionId, answerId)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("answerId", answerId)));
    }

    private String newToken() {
        byte[] random = new byte[STREAM_TOKEN_BYTES];
        secureRandom.nextBytes(random);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    public record Submitted(long answerId, String status, String token, long expiresIn) {}

    public record AnswerView(
            long id,
            long sessionId,
            long questionId,
            String inputMode,
            String answerText,
            Integer durationMs,
            String status,
            java.time.OffsetDateTime submittedAt) {}
}
