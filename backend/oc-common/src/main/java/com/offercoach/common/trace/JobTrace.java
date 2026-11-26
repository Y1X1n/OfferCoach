package com.offercoach.common.trace;

import java.util.UUID;
import org.slf4j.MDC;

/**
 * 定时任务的 trace 作用域：没有 HTTP 请求时也生成 requestId/traceId，
 * 让报告 worker、看门狗等后台日志可以按一次执行串联。
 *
 * <p>用法：{@code try (JobTrace ignored = JobTrace.open("report")) { ... }}</p>
 */
public final class JobTrace implements AutoCloseable {

    private final String previousRequestId;
    private final String previousTraceId;
    private final String previousUserId;
    private final String previousSessionId;

    private JobTrace(
            String previousRequestId, String previousTraceId, String previousUserId, String previousSessionId) {
        this.previousRequestId = previousRequestId;
        this.previousTraceId = previousTraceId;
        this.previousUserId = previousUserId;
        this.previousSessionId = previousSessionId;
    }

    public static JobTrace open(String name) {
        String previousRequestId = MDC.get(TraceContext.REQUEST_ID);
        String previousTraceId = MDC.get(TraceContext.TRACE_ID);
        String previousUserId = MDC.get(TraceContext.USER_ID);
        String previousSessionId = MDC.get(TraceContext.SESSION_ID);

        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        MDC.put(TraceContext.REQUEST_ID, "job_" + sanitize(name) + "_" + suffix);
        MDC.put(TraceContext.TRACE_ID, UUID.randomUUID().toString().replace("-", ""));
        MDC.remove(TraceContext.USER_ID);
        MDC.remove(TraceContext.SESSION_ID);
        return new JobTrace(previousRequestId, previousTraceId, previousUserId, previousSessionId);
    }

    @Override
    public void close() {
        restore(TraceContext.REQUEST_ID, previousRequestId);
        restore(TraceContext.TRACE_ID, previousTraceId);
        restore(TraceContext.USER_ID, previousUserId);
        restore(TraceContext.SESSION_ID, previousSessionId);
    }

    private static void restore(String key, String value) {
        if (value == null) {
            MDC.remove(key);
        } else {
            MDC.put(key, value);
        }
    }

    private static String sanitize(String name) {
        if (name == null || name.isBlank()) {
            return "task";
        }
        return name.replaceAll("[^A-Za-z0-9_-]", "_");
    }
}
