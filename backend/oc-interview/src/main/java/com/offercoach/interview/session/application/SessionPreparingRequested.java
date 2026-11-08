package com.offercoach.interview.session.application;

/**
 * 出题事件：会话已置 PREPARING，事务提交后由异步 worker 生成题目。
 */
public record SessionPreparingRequested(long sessionId, long userId) {}
