package com.offercoach.interview.session.application;

/**
 * 回答提交成功后的事件；事务提交后异步生成反馈，供前端轮询。
 */
public record AnswerSubmitted(long userId, long sessionId, long answerId) {}
