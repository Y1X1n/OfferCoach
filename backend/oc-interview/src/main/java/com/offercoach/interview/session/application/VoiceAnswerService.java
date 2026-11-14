package com.offercoach.interview.session.application;

import com.offercoach.ai.tools.api.ToolDtos;
import com.offercoach.ai.tools.error.ToolException;
import com.offercoach.ai.tools.gateway.AiToolGateway;
import com.offercoach.common.cache.RedisKeys;
import com.offercoach.common.error.BizException;
import com.offercoach.common.error.ErrorCode;
import com.offercoach.interview.session.application.port.InterviewAnswerRepository;
import com.offercoach.interview.session.application.port.InterviewQuestionRepository;
import com.offercoach.interview.session.domain.InterviewQuestion;
import com.offercoach.interview.storage.FileStorage;
import java.io.IOException;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 语音作答编排：会话与题目校验 → 音频落盘 → ASR 转写 → 复用文本作答链路提交。
 *
 * <p>转写失败时返回 ASR_FAILED 且不写回答、不扣额度，前端回退文本输入。</p>
 */
@Service
public class VoiceAnswerService {

    private static final Logger log = LoggerFactory.getLogger(VoiceAnswerService.class);
    private static final long MAX_AUDIO_BYTES = 8L * 1024 * 1024;
    private static final int MAX_DURATION_MS = 120_000;
    private static final long DAILY_VOICE_LIMIT = 60L;
    private static final String AUDIO_EXT_WAV = "wav";

    private final AnswerService answerService;
    private final InterviewQuestionRepository questionRepository;
    private final InterviewAnswerRepository answerRepository;
    private final FileStorage fileStorage;
    private final AiToolGateway toolGateway;
    private final StringRedisTemplate redisTemplate;

    public VoiceAnswerService(
            AnswerService answerService,
            InterviewQuestionRepository questionRepository,
            InterviewAnswerRepository answerRepository,
            FileStorage fileStorage,
            AiToolGateway toolGateway,
            StringRedisTemplate redisTemplate) {
        this.answerService = answerService;
        this.questionRepository = questionRepository;
        this.answerRepository = answerRepository;
        this.fileStorage = fileStorage;
        this.toolGateway = toolGateway;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 提交语音回答；返回与文本作答一致的结果，供前端轮询反馈。
     */
    public AnswerService.Submitted submit(
            long userId,
            long sessionId,
            long questionId,
            MultipartFile file,
            Integer durationMs,
            String idempotencyKey) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of("file", "音频为空")));
        }
        if (file.getSize() > MAX_AUDIO_BYTES) {
            throw new BizException(ErrorCode.FILE_TOO_LARGE, Map.of("maxBytes", MAX_AUDIO_BYTES));
        }
        if (durationMs != null && durationMs > MAX_DURATION_MS) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of("durationMs", "录音不得超过 120 秒")));
        }
        InterviewQuestion question = questionRepository
                .findOwned(sessionId, questionId)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("questionId", questionId)));
        enforceDailyLimit(userId);

        byte[] audio;
        try {
            audio = file.getBytes();
        } catch (IOException exception) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of("file", "音频读取失败")));
        }
        String contentType = normalizeContentType(file.getContentType());
        String fileName = "answer-" + questionId + "." + AUDIO_EXT_WAV;

        ToolDtos.AsrResult transcribed;
        try {
            transcribed = toolGateway.transcribe(userId, audio, fileName, contentType);
        } catch (ToolException exception) {
            log.warn("语音作答转写失败：questionId={} kind={}", questionId, exception.kind());
            throw new BizException(
                    ErrorCode.ASR_FAILED,
                    Map.of(
                            "reason",
                            exception.kind().name(),
                            "retryable",
                            exception.kind().retryable()));
        }
        String text = transcribed.text() == null ? "" : transcribed.text().strip();
        if (text.isEmpty()) {
            throw new BizException(ErrorCode.ASR_FAILED, Map.of("reason", "EMPTY_TRANSCRIPT"));
        }

        String audioKey = "voice/" + userId + "/" + UUID.randomUUID() + "." + AUDIO_EXT_WAV;
        fileStorage.store(audioKey, audio);
        try {
            Integer effectiveDuration = transcribed.durationMs() != null ? transcribed.durationMs() : durationMs;
            return answerService.submit(
                    userId, sessionId, questionId, text, effectiveDuration, "VOICE", idempotencyKey, audioKey, "DONE");
        } catch (RuntimeException exception) {
            fileStorage.delete(audioKey);
            throw exception;
        }
    }

    /**
     * ASR 失败后标记回答转写状态，用于审计与重试判定。
     */
    public void markAsrFailed(long userId, long sessionId, long answerId) {
        answerService.loadOwned(userId, sessionId, answerId);
        answerRepository.updateAsrStatus(answerId, "FAILED");
    }

    private void enforceDailyLimit(long userId) {
        String key =
                RedisKeys.dailyVoiceAnswer(userId, java.time.LocalDate.now().toString());
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, Duration.ofDays(1));
            }
            if (count != null && count > DAILY_VOICE_LIMIT) {
                throw new BizException(ErrorCode.RATE_LIMITED, Map.of("limit", DAILY_VOICE_LIMIT));
            }
        } catch (BizException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn("语音作答限流 Redis 不可用，放行：reason={}", exception.getClass().getSimpleName());
        }
    }

    private static String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "audio/wav";
        }
        String normalized = contentType.toLowerCase(Locale.ROOT);
        return normalized.startsWith("audio/") ? normalized : "audio/wav";
    }
}
