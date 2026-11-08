package com.offercoach.interview.api.dto.jd;

import java.math.BigDecimal;
import java.util.Map;

/**
 * JD 列表摘要项；含结构化字段，供面试配置页直接展示必备技能。
 */
public record JdSummaryResponse(
        String id,
        String title,
        String company,
        BigDecimal matchScore,
        Map<String, Object> structured,
        String createdAt) {}
