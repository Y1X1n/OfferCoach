package com.offercoach.interview.api.dto.interview;

import java.math.BigDecimal;

/**
 * 会话详情；progress 表示已答/跳过/总题数。
 */
public record SessionDetailResponse(
        String id,
        String status,
        ConfigView config,
        Progress progress,
        int currentSeq,
        BigDecimal totalScore,
        String jdTitle,
        String jdCompany,
        String reportId,
        String startedAt,
        String lastActiveAt,
        String createdAt) {

    public record ConfigView(
            int questionCount,
            String difficulty,
            String interviewerStyle,
            String answerMode,
            int timeLimitSec,
            String language) {}

    public record Progress(int answered, int skipped, int total) {}
}
