package com.offercoach.identity.api.dto;

/**
 * 注册 / 登录 / 刷新成功响应；expiresIn 单位为秒。
 */
public record AuthResponse(String userId, String accessToken, long expiresIn, UserSummary user) {}
