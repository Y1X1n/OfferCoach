package com.offercoach.identity.api.dto;

import java.time.OffsetDateTime;

/**
 * 当前用户资料。
 */
public record MeResponse(
        String id,
        String email,
        String nickname,
        String avatarUrl,
        String role,
        String timezone,
        String planCode,
        OffsetDateTime createdAt,
        boolean allowRawResumeToLlm) {}
