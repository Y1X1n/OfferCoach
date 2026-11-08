package com.offercoach.identity.api.dto;

/**
 * 认证响应中的用户摘要。
 */
public record UserSummary(String id, String nickname, String role, String planCode) {}
