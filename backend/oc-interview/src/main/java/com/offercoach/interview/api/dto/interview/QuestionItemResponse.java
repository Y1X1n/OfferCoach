package com.offercoach.interview.api.dto.interview;

import java.util.List;

/**
 * 当前可见题目。
 */
public record QuestionItemResponse(
        String id,
        int seq,
        String dimension,
        String difficulty,
        String text,
        String source,
        List<String> referencePoints,
        String status,
        String whyThisQuestion,
        String answerText,
        Integer durationMs,
        String answerId) {}
