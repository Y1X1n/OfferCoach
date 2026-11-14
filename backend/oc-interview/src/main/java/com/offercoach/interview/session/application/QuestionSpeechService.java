package com.offercoach.interview.session.application;

import com.offercoach.ai.tools.error.ToolException;
import com.offercoach.ai.tools.gateway.AiToolGateway;
import com.offercoach.common.cache.RedisKeys;
import com.offercoach.common.error.BizException;
import com.offercoach.common.error.ErrorCode;
import com.offercoach.interview.session.application.port.InterviewQuestionRepository;
import com.offercoach.interview.session.application.port.InterviewSessionRepository;
import com.offercoach.interview.session.domain.InterviewQuestion;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 题目朗读编排：校验会话与题目归属后调用 TTS 服务，返回音频字节。
 *
 * <p>同一题同一音色在 TTS 服务侧按内容哈希缓存；本侧只做每日次数限制。</p>
 */
@Service
public class QuestionSpeechService {

    private static final Logger log = LoggerFactory.getLogger(QuestionSpeechService.class);
    private static final long DAILY_LIMIT = 300L;
    private static final int MAX_TEXT_CHARS = 4000;

    private final InterviewSessionRepository sessionRepository;
    private final InterviewQuestionRepository questionRepository;
    private final AiToolGateway toolGateway;
    private final StringRedisTemplate redisTemplate;

    public QuestionSpeechService(
            InterviewSessionRepository sessionRepository,
            InterviewQuestionRepository questionRepository,
            AiToolGateway toolGateway,
            StringRedisTemplate redisTemplate) {
        this.sessionRepository = sessionRepository;
        this.questionRepository = questionRepository;
        this.toolGateway = toolGateway;
        this.redisTemplate = redisTemplate;
    }

    public Speech speak(long userId, long sessionId, long questionId, String voiceId) {
        sessionRepository
                .findOwned(userId, sessionId)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("sessionId", sessionId)));
        InterviewQuestion question = questionRepository
                .findOwned(sessionId, questionId)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("questionId", questionId)));
        String text = question.text();
        if (text == null || text.isBlank()) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of("questionId", "题目为空")));
        }
        if (text.length() > MAX_TEXT_CHARS) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of("questionId", "题目过长")));
        }
        enforceDailyLimit(userId);
        try {
            AiToolGateway.AudioResult audio = toolGateway.speak(userId, text, voiceId, "wav");
            return new Speech(audio.audio(), audio.contentType());
        } catch (ToolException exception) {
            log.warn("题目朗读失败：questionId={} kind={}", questionId, exception.kind());
            throw new BizException(
                    ErrorCode.AI_UNAVAILABLE,
                    Map.of(
                            "reason",
                            exception.kind().name(),
                            "retryable",
                            exception.kind().retryable()));
        }
    }

    private void enforceDailyLimit(long userId) {
        String key = RedisKeys.dailyTts(userId, java.time.LocalDate.now().toString());
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, Duration.ofDays(1));
            }
            if (count != null && count > DAILY_LIMIT) {
                throw new BizException(ErrorCode.RATE_LIMITED, Map.of("limit", DAILY_LIMIT));
            }
        } catch (BizException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn("题目朗读限流 Redis 不可用，放行：reason={}", exception.getClass().getSimpleName());
        }
    }

    /**
     * 朗读音频。
     */
    public record Speech(byte[] audio, String contentType) {}
}
