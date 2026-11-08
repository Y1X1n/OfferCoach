package com.offercoach.common.trace;

import org.slf4j.MDC;

public final class TraceContext {

    public static final String REQUEST_ID = "requestId";
    public static final String TRACE_ID = "traceId";
    public static final String USER_ID = "userId";

    private TraceContext() {}

    public static String requestId() {
        return MDC.get(REQUEST_ID);
    }

    public static String traceId() {
        return MDC.get(TRACE_ID);
    }

    public static void putUserId(String userId) {
        if (userId != null && !userId.isBlank()) {
            MDC.put(USER_ID, userId);
        }
    }
}
