package com.offercoach.interview.api;

import com.offercoach.common.error.BizException;
import com.offercoach.common.error.ErrorCode;
import com.offercoach.identity.security.CurrentUser;
import com.offercoach.interview.feedback.application.FeedbackStreamService;
import java.util.Map;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
        if (!streamService.validToken(userId, parsedAnswerId, token)) {
            throw new BizException(ErrorCode.FORBIDDEN, Map.of("reason", "STREAM_TOKEN_INVALID"));
        }
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        sseExecutor.execute(() -> streamService.stream(emitter, userId, parsedAnswerId));
        return emitter;
    }
}
