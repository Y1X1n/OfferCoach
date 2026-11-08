package com.offercoach.interview.resume.application;

/**
 * 简历解析请求事件；在创建/重解析事务提交后触发异步解析。
 */
public record ResumeParseRequested(long resumeId) {}
