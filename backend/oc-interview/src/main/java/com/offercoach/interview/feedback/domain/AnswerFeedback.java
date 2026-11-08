package com.offercoach.interview.feedback.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 回答反馈；dimensions 固定为 content/structure/depth/fit/language 五个 0-10 分维度。
 */
public record AnswerFeedback(
        long id,
        long answerId,
        long sessionId,
        long userId,
        int version,
        String requestId,
        BigDecimal overallScore,
        Map<String, BigDecimal> dimensions,
        List<String> strengths,
        List<String> improvements,
        List<FeedbackEvidence> evidence,
        String rewrittenAnswer,
        String model,
        String provider,
        boolean degraded,
        OffsetDateTime createdAt) {}
