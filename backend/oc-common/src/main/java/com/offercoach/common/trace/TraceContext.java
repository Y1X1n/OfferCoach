package com.offercoach.common.trace;

import org.slf4j.MDC;

public final class TraceContext {

    public static final String REQUEST_ID = "requestId";
    public static final String TRACE_ID = "traceId";
    public static final String USER_ID = "userId";
    public static final String SESSION_ID = "sessionId";

    private TraceContext() {}

    public static String requestId() {
        return MDC.get(REQUEST_ID);
    }

    public static String traceId() {
        return MDC.get(TRACE_ID);
    }

    public static String sessionId() {
        return MDC.get(SESSION_ID);
    }

    public static void putUserId(String userId) {
        if (userId != null && !userId.isBlank()) {
            MDC.put(USER_ID, userId);
        }
    }

    public static void putSessionId(long sessionId) {
        MDC.put(SESSION_ID, Long.toString(sessionId));
    }

    /**
     * 清理请求/trace 上下文；由最外层 {@code RequestIdFilter} 统一调用，避免线程复用串号。
     */
    public static void clear() {
        MDC.remove(REQUEST_ID);
        MDC.remove(TRACE_ID);
        MDC.remove(USER_ID);
        MDC.remove(SESSION_ID);
    }
}
