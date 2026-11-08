package com.offercoach.interview.api.dto.jd;

import java.util.Map;

/**
 * 创建 JD 响应。
 */
public record JdCreatedResponse(String id, String title, Map<String, Object> structured, String createdAt) {}
