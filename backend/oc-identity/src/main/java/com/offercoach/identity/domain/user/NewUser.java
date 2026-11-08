package com.offercoach.identity.domain.user;

/**
 * 新建账号的领域输入；ID 与时间戳由仓储在落库时补齐。
 */
public record NewUser(
        String email, String passwordHash, String nickname, String role, String status, String timezone) {}
