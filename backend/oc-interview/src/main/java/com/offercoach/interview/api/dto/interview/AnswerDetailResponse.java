package com.offercoach.interview.api.dto.interview;

/**
 * 回答详情；不包含评分结果。
 */
public record AnswerDetailResponse(
        String answerId,
        String questionId,
        String sessionId,
        String inputMode,
        String answerText,
        Integer durationMs,
        String status,
        String submittedAt) {}
