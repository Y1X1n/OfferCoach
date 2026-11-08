package com.offercoach.interview.api.dto.jd;

import jakarta.validation.constraints.NotBlank;

/**
 * 简历匹配请求。
 */
public record JdMatchRequest(@NotBlank(message = "resumeId 不能为空") String resumeId) {}
