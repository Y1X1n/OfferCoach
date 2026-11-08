package com.offercoach.interview.session.domain;

import java.util.Set;

/**
 * 会话状态常量与“未结束”状态集合；与 interview_session.status 检查约束一致。
 */
public final class SessionStatus {

    public static final String CREATED = "CREATED";
    public static final String PREPARING = "PREPARING";
    public static final String READY = "READY";
    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String PAUSED = "PAUSED";
    public static final String FINISHED = "FINISHED";
    public static final String REPORT_GENERATING = "REPORT_GENERATING";
    public static final String REPORT_FAILED = "REPORT_FAILED";
    public static final String COMPLETED = "COMPLETED";
    public static final String FAILED = "FAILED";
    public static final String ABANDONED = "ABANDONED";

    /** 视为“未结束”的状态，同一用户同时只允许一个。 */
    public static final Set<String> ACTIVE = Set.of(CREATED, PREPARING, READY, IN_PROGRESS, PAUSED);

    /** 可主动放弃的状态。 */
    public static final Set<String> ABANDONABLE = ACTIVE;

    private SessionStatus() {}
}
