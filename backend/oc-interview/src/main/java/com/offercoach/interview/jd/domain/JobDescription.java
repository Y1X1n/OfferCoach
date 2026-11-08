package com.offercoach.interview.jd.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * JD 领域视图；structuredJson / matchReport 为 JSONB 的文本形式。
 */
public record JobDescription(
        long id,
        long userId,
        String title,
        String company,
        String rawText,
        String structuredJson,
        BigDecimal matchScore,
        String matchReport,
        String contentHash,
        OffsetDateTime createdAt) {}
