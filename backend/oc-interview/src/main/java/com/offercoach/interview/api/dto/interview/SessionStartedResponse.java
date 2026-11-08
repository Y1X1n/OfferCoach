package com.offercoach.interview.api.dto.interview;

/**
 * 开始出题受理响应。
 */
public record SessionStartedResponse(String id, String status, long estimatedReadyInMs) {}
