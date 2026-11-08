package com.offercoach.interview.api.dto.resume;

/**
 * 重解析受理结果。
 */
public record ResumeReparseResponse(String id, String parseStatus, long estimatedReadyInMs) {}
