package com.offercoach.interview.api.dto.resume;

/**
 * 简历列表摘要项，不含 structuredJson / rawText。
 */
public record ResumeSummaryResponse(
        String id,
        String title,
        String parseStatus,
        String sourceType,
        String fileName,
        Integer pageCount,
        String summary,
        boolean isDefault,
        String parseError,
        String createdAt) {}
