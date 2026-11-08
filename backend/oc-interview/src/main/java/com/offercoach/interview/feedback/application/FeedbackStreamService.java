package com.offercoach.interview.feedback.application;

import com.offercoach.ai.AiScene;
import com.offercoach.common.cache.RedisKeys;
import com.offercoach.common.error.BizException;
import com.offercoach.common.error.ErrorCode;
import com.offercoach.interview.ai.AiGateway;
import com.offercoach.interview.feedback.domain.AnswerFeedback;
import com.offercoach.interview.feedback.domain.FeedbackEvidence;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 评分 SSE：先落库再输出；模型不可用时按固定节奏分块输出确定性反馈。
 */
@Service
public class FeedbackStreamService {

    private static final Logger log = LoggerFactory.getLogger(FeedbackStreamService.class);
    private static final String SYSTEM_PROMPT =
            """
            你是资深技术面试教练。请针对候选人的回答输出中文反馈，包含：整体评价、至少 2 条优点、
            至少 2 条改进建议、原文证据和一段 STAR 结构改写建议。不要输出 JSON，不要编造回答中不存在的内容。
            """;

    private final FeedbackService feedbackService;
    private final AiGateway aiGateway;
    private final StringRedisTemplate redisTemplate;

    public FeedbackStreamService(
            FeedbackService feedbackService, AiGateway aiGateway, StringRedisTemplate redisTemplate) {
        this.feedbackService = feedbackService;
        this.aiGateway = aiGateway;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 校验短期令牌；返回 true 表示允许建立评分流。
     */
    public boolean validToken(long userId, long answerId, String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            String stored = redisTemplate.opsForValue().get(RedisKeys.aiStreamToken(token));
            return (userId + ":" + answerId).equals(stored);
        } catch (RuntimeException exception) {
            log.warn("评分流令牌校验失败：reason={}", exception.getClass().getSimpleName());
            return false;
        }
    }

    /**
     * 向 SSE 输出评分；执行线程由调用方的线程池提供。
     */
    public void stream(SseEmitter emitter, long userId, long answerId) {
        AtomicInteger sequence = new AtomicInteger();
        try {
            FeedbackService.Prepared prepared = feedbackService.prepare(userId, answerId);
            AnswerFeedback feedback = prepared.feedback();
            send(
                    emitter,
                    "meta",
                    Map.of(
                            "answerId", Long.toString(answerId),
                            "model", feedback.model(),
                            "provider", feedback.provider(),
                            "degraded", feedback.degraded()));
            String fallback = narrative(feedback);
            send(emitter, "section", Map.of("name", "overview"));
            boolean aiStreamed = aiGateway.stream(
                    AiScene.FEEDBACK,
                    aiGateway.systemPrompt(AiScene.FEEDBACK, SYSTEM_PROMPT),
                    prompt(prepared),
                    delta -> sendToken(emitter, sequence, delta));
            if (!aiStreamed) {
                chunk(emitter, sequence, fallback);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("feedbackId", Long.toString(feedback.id()));
            result.put("overallScore", feedback.overallScore());
            result.put("dimensions", feedback.dimensions());
            send(emitter, "result", result);
            emitter.complete();
        } catch (BizException exception) {
            sendError(emitter, exception.errorCode().code(), exception.getMessage(), false);
        } catch (RuntimeException exception) {
            log.warn(
                    "评分流中断：answerId={} reason={}",
                    answerId,
                    exception.getClass().getSimpleName());
            sendError(emitter, ErrorCode.INTERNAL_ERROR.code(), "评分流中断，请重试", true);
        }
    }

    private String prompt(FeedbackService.Prepared prepared) {
        AnswerFeedback feedback = prepared.feedback();
        return "题目：" + prepared.question().text()
                + "\n评分参考点：" + String.join("、", prepared.question().referencePoints())
                + "\n候选人回答：" + truncate(prepared.answer().answerText(), 3500)
                + "\n确定性评分：内容 " + feedback.dimensions().get("content")
                + "，结构 " + feedback.dimensions().get("structure")
                + "，深度 " + feedback.dimensions().get("depth")
                + "，匹配 " + feedback.dimensions().get("fit")
                + "，语言 " + feedback.dimensions().get("language")
                + "。请围绕这些分数解释原因并给出可执行建议。";
    }

    private static String narrative(AnswerFeedback feedback) {
        BigDecimal overall = feedback.overallScore();
        StringBuilder builder = new StringBuilder("本题得分 " + overall.toPlainString() + " / 10。\n\n");
        builder.append("五维表现：内容 ")
                .append(feedback.dimensions().get("content"))
                .append("，结构 ")
                .append(feedback.dimensions().get("structure"))
                .append("，深度 ")
                .append(feedback.dimensions().get("depth"))
                .append("，匹配 ")
                .append(feedback.dimensions().get("fit"))
                .append("，语言 ")
                .append(feedback.dimensions().get("language"))
                .append("。\n\n优点\n");
        for (int index = 0; index < feedback.strengths().size(); index++) {
            builder.append(index + 1)
                    .append(". ")
                    .append(feedback.strengths().get(index))
                    .append('\n');
        }
        builder.append("\n改进\n");
        for (int index = 0; index < feedback.improvements().size(); index++) {
            builder.append(index + 1)
                    .append(". ")
                    .append(feedback.improvements().get(index))
                    .append('\n');
        }
        if (!feedback.evidence().isEmpty()) {
            builder.append("\n证据\n");
            for (FeedbackEvidence item : feedback.evidence()) {
                builder.append("“")
                        .append(item.quote())
                        .append("”——")
                        .append(item.reason())
                        .append('\n');
            }
        }
        if (feedback.rewrittenAnswer() != null) {
            builder.append("\n建议改写\n").append(feedback.rewrittenAnswer());
        }
        return builder.toString();
    }

    private static void chunk(SseEmitter emitter, AtomicInteger sequence, String text) {
        int index = 0;
        while (index < text.length()) {
            int size = Math.min(index < 60 ? 3 : 6, text.length() - index);
            sendToken(emitter, sequence, text.substring(index, index + size));
            index += size;
            try {
                Thread.sleep(18L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static void sendToken(SseEmitter emitter, AtomicInteger sequence, String delta) {
        try {
            emitter.send(
                    SseEmitter.event().name("token").data(Map.of("seq", sequence.incrementAndGet(), "delta", delta)));
        } catch (IOException exception) {
            throw new ClientGoneException(exception);
        }
    }

    private static void send(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (IOException exception) {
            throw new ClientGoneException(exception);
        }
    }

    private static void sendError(SseEmitter emitter, String code, String message, boolean retryable) {
        try {
            emitter.send(SseEmitter.event()
                    .name("error")
                    .data(Map.of("code", code, "message", message, "retryable", retryable)));
            emitter.complete();
        } catch (IOException ignored) {
            emitter.completeWithError(ignored);
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static final class ClientGoneException extends RuntimeException {
        private ClientGoneException(Throwable cause) {
            super(cause);
        }
    }
}
