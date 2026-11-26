package com.offercoach.interview.session.application;

import com.offercoach.ai.AiRuntimeConfigService;
import com.offercoach.ai.tools.error.ToolErrorKind;
import com.offercoach.ai.tools.error.ToolException;
import com.offercoach.ai.tools.gateway.AiToolGateway;
import com.offercoach.common.cache.RedisKeys;
import com.offercoach.common.error.BizException;
import com.offercoach.common.error.ErrorCode;
import com.offercoach.common.trace.TraceContext;
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
 * <p>同一题同一音色在 TTS 服务侧按内容哈希缓存；本侧只做每题次数限制。
 * 计数前先校验能力开关，关闭时不计数；上游失败时归还计数，做到"未产出不扣次"。</p>
 */
@Service
public class QuestionSpeechService {

    private static final Logger log = LoggerFactory.getLogger(QuestionSpeechService.class);
    private static final int MAX_TEXT_CHARS = 4000;
    /** 计数键仅作过期清理，语义是"每题上限"（见 api.md §9.2），不按日重置。 */
    private static final Duration QUESTION_LIMIT_TTL = Duration.ofDays(30);

    private final InterviewSessionRepository sessionRepository;
    private final InterviewQuestionRepository questionRepository;
    private final AiToolGateway toolGateway;
    private final AiRuntimeConfigService runtimeConfig;
    private final RedisCounterGuard counterGuard;
    private final PlatformSettingsService platformSettings;

    public QuestionSpeechService(
            InterviewSessionRepository sessionRepository,
            InterviewQuestionRepository questionRepository,
            AiToolGateway toolGateway,
            AiRuntimeConfigService runtimeConfig,
            RedisCounterGuard counterGuard,
            PlatformSettingsService platformSettings) {
        this.sessionRepository = sessionRepository;
        this.questionRepository = questionRepository;
        this.toolGateway = toolGateway;
        this.runtimeConfig = runtimeConfig;
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
        if (!runtimeConfig.tools().ttsEnabled()) {
            throw new BizException(
                    ErrorCode.AI_UNAVAILABLE, Map.of("reason", "NOT_CONFIGURED", "message", "题目朗读已被管理员关闭"));
        }
        String text = question.text();
        if (text == null || text.isBlank()) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of("questionId", "题目为空")));
        }
        if (text.length() > MAX_TEXT_CHARS) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of("questionId", "题目过长")));
        }
        String counterKey = RedisKeys.ttsPerQuestion(userId, questionId);
        boolean counted = enforceQuestionLimit(counterKey);
        TraceContext.putSessionId(sessionId);
        try {
            AiToolGateway.AudioResult audio = toolGateway.speak(userId, text, voiceId, "wav");
            return new Speech(audio.audio(), audio.contentType());
        } catch (ToolException exception) {
            if (counted) {
                counterGuard.release(counterKey);
            }
            log.warn("题目朗读失败：questionId={} kind={}", questionId, exception.kind());
            throw new BizException(
                    mapErrorCode(exception.kind()),
                    Map.of(
                            "reason",
                            exception.kind().name(),
                            "retryable",
                            exception.kind().retryable()));
        }
    }

    /**
     * 按上游错误分类映射到对外错误码。
     *
     * <p>全部归为 503 会把「内容被拒（422）」和「并发超限（429）」也报成服务不可用，
     * 前端据此重试或提示都会跑偏。</p>
     */
    private static ErrorCode mapErrorCode(ToolErrorKind kind) {
        return switch (kind) {
            case CONTENT_REJECTED -> ErrorCode.CONTENT_REJECTED;
            case TOO_LARGE -> ErrorCode.FILE_TOO_LARGE;
            case UNSUPPORTED_FORMAT -> ErrorCode.UNSUPPORTED_FILE;
            case INVALID_INPUT -> ErrorCode.VALIDATION_FAILED;
            case BUSY, UPSTREAM_BUSY -> ErrorCode.RATE_LIMITED;
            case UPSTREAM_TIMEOUT -> ErrorCode.AI_TIMEOUT;
            default -> ErrorCode.AI_UNAVAILABLE;
        };
    }

    /**
     * 单题朗读次数上限（平台策略可配，0 表示不限）；返回是否真正计数。
     */
    private boolean enforceQuestionLimit(String counterKey) {
        int limit = platformSettings.snapshot().ttsPerQuestionLimit();
        if (limit <= 0) {
            return false;
        }
        counterGuard.tryAcquireOrThrow(counterKey, limit, QUESTION_LIMIT_TTL, "PER_QUESTION");
        return true;
    }

    /**
     * 朗读音频。
     */
    public record Speech(byte[] audio, String contentType) {}
}
