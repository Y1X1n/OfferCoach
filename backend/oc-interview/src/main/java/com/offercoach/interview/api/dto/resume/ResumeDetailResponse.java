package com.offercoach.interview.api.dto.resume;

import java.util.List;
import java.util.Map;

/**
 * 简历详情；structured 对应 data-model.md §4 约定。
 */
public record ResumeDetailResponse(
        String id,
        String title,
        String parseStatus,
        String sourceType,
        String fileName,
        String mimeType,
        Long fileSize,
        Integer pageCount,
        Map<String, Object> structured,
        String summary,
        List<String> lowConfidenceFields,
        String parseError,
        boolean isDefault,
        String createdAt) {}
