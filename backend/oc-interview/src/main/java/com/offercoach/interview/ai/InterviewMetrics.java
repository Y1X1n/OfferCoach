package com.offercoach.interview.ai;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * 面试域业务指标：AI 调用量/耗时、报告生成结果与耗时。
 *
 * <p>标签只使用低基数的场景、状态、供应商；userId/sessionId 不进入 Prometheus。</p>
 */
@Component
public class InterviewMetrics {

    private static final String UNKNOWN = "unknown";

    private final MeterRegistry registry;

    public InterviewMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void aiCall(String provider, String scene, String status, long latencyMs) {
        registry.counter("oc.ai.calls", "provider", tag(provider), "scene", tag(scene), "status", tag(status))
                .increment();
        registry.timer("oc.ai.latency", "provider", tag(provider), "scene", tag(scene), "status", tag(status))
                .record(Duration.ofMillis(Math.max(0, latencyMs)));
    }

    public void reportGeneration(String status, long durationMs) {
        registry.timer("oc.report.generation", "status", tag(status))
                .record(Duration.ofMillis(Math.max(0, durationMs)));
        registry.counter("oc.report.generation.total", "status", tag(status)).increment();
    }

    public void feedbackGenerated(String status) {
        registry.counter("oc.feedback.generated", "status", tag(status)).increment();
    }

    private static String tag(String value) {
        return value == null || value.isBlank() ? UNKNOWN : value;
    }
}
