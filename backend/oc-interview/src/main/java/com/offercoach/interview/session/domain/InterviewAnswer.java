package com.offercoach.interview.session.domain;

import java.time.OffsetDateTime;

/**
 * 回答领域视图；answerText 仅在反馈评分与详情接口按归属读取，禁止写入日志。
 */
public record InterviewAnswer(
        long id,
        long sessionId,
        long questionId,
        long userId,
        String inputMode,
        String answerText,
        Integer durationMs,
        String status,
        OffsetDateTime submittedAt) {}
