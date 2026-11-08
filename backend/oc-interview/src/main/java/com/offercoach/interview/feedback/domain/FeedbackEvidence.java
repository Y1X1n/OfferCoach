package com.offercoach.interview.feedback.domain;

/**
 * 评分证据：引用回答原文片段并说明评分理由，最多 3 条。
 */
public record FeedbackEvidence(String quote, String reason) {}
