package com.offercoach.interview.session.application;

import com.offercoach.ai.tools.error.ToolException;
import com.offercoach.ai.tools.gateway.AiToolGateway;
import com.offercoach.common.cache.RedisKeys;
import com.offercoach.common.error.BizException;
import com.offercoach.common.error.ErrorCode;
import com.offercoach.identity.application.quota.RedisCounterGuard;
import com.offercoach.identity.application.settings.PlatformSettingsService;
import com.offercoach.interview.session.application.port.InterviewQuestionRepository;
import com.offercoach.interview.session.application.port.InterviewSessionRepository;
import com.offercoach.interview.session.domain.InterviewQuestion;
import com.offercoach.interview.session.domain.InterviewSession;
import com.offercoach.interview.session.domain.SessionStatus;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 题目朗读编排：校验会话与题目归属后调用 TTS 服务，返回音频字节。
 *
 * <p>同一题同一音色在 TTS 服务侧按内容哈希缓存；本侧只做每日次数限制。</p>
 */
@Service
public class QuestionSpeechService {

    private static final Logger log = LoggerFactory.getLogger(QuestionSpeechService.class);
    private static final int MAX_TEXT_CHARS = 4000;

    private final InterviewSessionRepository sessionRepository;
    private final InterviewQuestionRepository questionRepository;
    private final AiToolGateway toolGateway;
    private final RedisCounterGuard counterGuard;
    private final PlatformSettingsService platformSettings;

    public QuestionSpeechService(
            InterviewSessionRepository sessionRepository,
            InterviewQuestionRepository questionRepository,
            AiToolGateway toolGateway,
            RedisCounterGuard counterGuard,
            PlatformSettingsService platformSettings) {
        this.sessionRepository = sessionRepository;
        this.questionRepository = questionRepository;
        this.toolGateway = toolGateway;
        this.counterGuard = counterGuard;
        this.platformSettings = platformSettings;
    }

    public Speech speak(long userId, long sessionId, long questionId, String voiceId) {
        InterviewSession session = sessionRepository
                .findOwned(userId, sessionId)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("sessionId", sessionId)));
        // 已结束/已放弃的会话不再合成，避免无意义的上游费用。
        if (!SessionStatus.READY.equals(session.status())
                && !SessionStatus.IN_PROGRESS.equals(session.status())
                && !SessionStatus.PAUSED.equals(session.status())) {
            throw new BizException(ErrorCode.STATE_CONFLICT, Map.of("status", session.status()));
        }
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
        enforceQuestionLimit(userId, questionId);
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

    /**
     * 单题朗读次数上限（平台策略可配，0 表示不限）。
     */
    private void enforceQuestionLimit(long userId, long questionId) {
        int limit = platformSettings.snapshot().ttsPerQuestionLimit();
        if (limit <= 0) {
            return;
        }
        counterGuard.tryAcquireOrThrow(
                RedisKeys.ttsPerQuestion(userId, questionId), limit, Duration.ofDays(1), "PER_QUESTION");
    }

    /**
     * 朗读音频。
     */
    public record Speech(byte[] audio, String contentType) {}
}
