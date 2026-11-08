package com.offercoach.interview.api.dto.jd;

import java.math.BigDecimal;
import java.util.Map;

/**
 * JD 详情；matchReport 为最近一次匹配结果。
 */
public record JdDetailResponse(
        String id,
        String title,
        String company,
        String rawText,
        Map<String, Object> structured,
        BigDecimal matchScore,
        Map<String, Object> matchReport,
        String createdAt) {}
