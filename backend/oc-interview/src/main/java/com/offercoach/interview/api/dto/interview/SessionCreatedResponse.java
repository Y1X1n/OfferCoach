package com.offercoach.interview.api.dto.interview;

/**
 * 创建会话响应，结构与 api.md §6.1 一致。
 */
public record SessionCreatedResponse(String id, String status, int questionCount, Quota quota, String createdAt) {

    public record Quota(int charged, int balance) {}
}
