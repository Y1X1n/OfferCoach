package com.offercoach.interview.session.domain;

import java.util.List;

/**
 * 面试题目领域视图；referencePoints 由 JSONB 解析为字符串列表。
 */
public record InterviewQuestion(
        long id,
        long sessionId,
        int seq,
        String dimension,
        String difficulty,
        String text,
        String source,
        Long bankQuestionId,
        List<String> referencePoints,
        String whyThisQuestion,
        String retrievalJson,
        String contextJson,
        String status) {}
