package com.offercoach.interview.jd.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * JD 领域视图；structuredJson / matchReport 为 JSONB 的文本形式。
 *
 * <p>sourceType 记录来源（MANUAL 手工粘贴 / SEARCH 搜索兜底 / URL 链接抓取），
 * sourceUrl 在联网获取时记录原始链接，便于前端展示来源与追溯。</p>
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
        OffsetDateTime createdAt,
        String sourceType,
        String sourceUrl) {

    public static final String SOURCE_MANUAL = "MANUAL";
    public static final String SOURCE_SEARCH = "SEARCH";
    public static final String SOURCE_URL = "URL";

    /**
     * 手工粘贴来源的默认值。
     */
    public static String defaultSourceType() {
        return SOURCE_MANUAL;
    }
}
