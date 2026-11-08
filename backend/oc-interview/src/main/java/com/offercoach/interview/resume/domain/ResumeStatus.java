package com.offercoach.interview.resume.domain;

/**
 * 简历解析状态常量，与 resume.parse_status 的 CHECK 约束一致。
 */
public final class ResumeStatus {

    public static final String PENDING = "PENDING";
    public static final String PARSING = "PARSING";
    public static final String READY = "READY";
    public static final String FAILED = "FAILED";

    private ResumeStatus() {}
}
