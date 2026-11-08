package com.offercoach.interview.session.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 面试会话领域视图；configJson 为 JSONB 的文本形式，在应用层解析。
 */
public record InterviewSession(
        long id,
        long userId,
        long resumeId,
        long jdId,
        String status,
        String configJson,
        int questionCount,
        int answeredCount,
        int currentSeq,
        BigDecimal totalScore,
        String failureReason,
        String idempotencyKey,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        OffsetDateTime lastActiveAt,
        OffsetDateTime createdAt) {}
