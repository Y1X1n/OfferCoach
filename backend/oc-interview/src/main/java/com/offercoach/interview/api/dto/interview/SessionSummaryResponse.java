package com.offercoach.interview.api.dto.interview;

import java.math.BigDecimal;

/**
 * 会话列表摘要。
 */
public record SessionSummaryResponse(
        String id,
        String status,
        int questionCount,
        int answeredCount,
        int currentSeq,
        BigDecimal totalScore,
        String resumeId,
        String jdId,
        String createdAt,
        String lastActiveAt) {}
