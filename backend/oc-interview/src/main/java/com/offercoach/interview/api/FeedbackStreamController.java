package com.offercoach.interview.api;

import com.offercoach.common.error.BizException;
import com.offercoach.common.error.ErrorCode;
import com.offercoach.identity.security.CurrentUser;
import com.offercoach.interview.feedback.application.FeedbackStreamService;
import java.util.Map;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 评分 SSE：Authorization + 短期 stream token 双校验。
 */
@RestController
@RequestMapping("/api/v1/ai")
@ConditionalOnProperty(name = "oc.ai.sse-enabled", havingValue = "true")
public class FeedbackStreamController {

    private static final long STREAM_TIMEOUT_MS = 300_000L;

    private final FeedbackStreamService streamService;
    private final CurrentUser currentUser;
    private final Executor sseExecutor;

    public FeedbackStreamController(
            FeedbackStreamService streamService,
            CurrentUser currentUser,
            @Qualifier("sseExecutor") Executor sseExecutor) {
        this.streamService = streamService;
        this.currentUser = currentUser;
        this.sseExecutor = sseExecutor;
    }

    @GetMapping("/feedback/stream")
    public SseEmitter stream(@RequestParam("answerId") String answerId, @RequestParam("token") String token) {
        long userId = currentUser.userId();
        long parsedAnswerId;
        try {
            parsedAnswerId = Long.parseLong(answerId);
        } catch (NumberFormatException exception) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of("answerId", "非数字 ID")));
        }
        switch (streamService.checkToken(userId, parsedAnswerId, token)) {
            case INVALID -> throw new BizException(ErrorCode.FORBIDDEN, Map.of("reason", "STREAM_TOKEN_INVALID"));
            case UNAVAILABLE -> throw new BizException(
                    ErrorCode.AI_UNAVAILABLE, Map.of("reason", "STREAM_TOKEN_STORE_UNAVAILABLE"));
            default -> {
                // VALID：继续建立流
            }
        }
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        try {
            sseExecutor.execute(() -> streamService.stream(emitter, userId, parsedAnswerId));
        } catch (TaskRejectedException exception) {
            // 线程池已满：以错误事件收尾，避免 500 + 永不完成的 emitter。
            emitter.completeWithError(exception);
        }
        return emitter;
    }
}
