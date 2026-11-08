package com.offercoach.identity.domain.user;

import java.time.OffsetDateTime;

/**
 * 账号领域视图：供 Application 层编排使用，不直接对应持久化 DO。
 *
 * <p>{@code passwordHash} 只用于认证时的密码校验，不得写入日志或对外返回。</p>
 */
public record UserAccount(
        long id,
        String email,
        String passwordHash,
        String nickname,
        String avatarUrl,
        String role,
        String status,
        String timezone,
        OffsetDateTime createdAt,
        OffsetDateTime lastLoginAt,
        OffsetDateTime deletedAt) {

    private static final String STATUS_ACTIVE = "ACTIVE";

    /**
     * 账号是否可登录：状态为 ACTIVE 且未被软删。
     */
    public boolean isActive() {
        return STATUS_ACTIVE.equals(status) && deletedAt == null;
    }
}
