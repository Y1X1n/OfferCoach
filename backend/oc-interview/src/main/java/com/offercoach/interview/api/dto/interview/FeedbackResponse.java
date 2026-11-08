package com.offercoach.interview.api.dto.interview;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 评分结果；FREE 套餐 rewrittenAnswer 为 null。
 */
public record FeedbackResponse(
        String feedbackId,
        int version,
        String answerId,
        BigDecimal overallScore,
        Map<String, BigDecimal> dimensions,
        List<String> strengths,
        List<String> improvements,
        List<Evidence> evidence,
        String rewrittenAnswer,
        String model,
        boolean degraded,
        String createdAt) {

    public record Evidence(String quote, String reason) {}
}
