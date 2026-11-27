package com.offercoach.ai;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * AI 调用的并发闸门 + 连续失败熔断。
 *
 * <p>供应商变慢或挂起时，如果放任请求排队，占用的是 Tomcat 的请求线程：默认 200 个线程
 * 会被 60 秒超时的 AI 调用逐个占满，随后鉴权、额度、支付回调这些纯 DB 接口全部无响应——
 * 一次上游抖动会升级成整站不可用，连"付款成功却没发额度"都会被连带触发。</p>
 *
 * <p>这里做两件事：
 * <ul>
 *   <li><b>并发上限</b>：同时在途的 AI 调用不超过 {@code oc.ai.max-concurrent-calls}，
 *       拿不到名额就在短暂的 {@code queue-wait} 后抛 {@link AiUnavailableException}，
 *       由业务层立刻走启发式/模板降级，而不是把线程挂在 60 秒超时上。</li>
 *   <li><b>连续失败熔断</b>：连续 {@code breaker-failure-threshold} 次上游失败后，
 *       在 {@code breaker-open-duration} 内直接判定不可用（省掉超时等待），到点自动放行。</li>
 * </ul>
 *
 * <p>只有<b>上游不可用</b>类失败（IO 异常、5xx、429、响应为空）才计入熔断；
 * 请求参数错误（4xx）与响应解析失败不计，否则一条坏 Prompt 就能把全站 AI 熔断 30 秒。</p>
 */
@Component
public class AiCallGuard {

    private static final Logger log = LoggerFactory.getLogger(AiCallGuard.class);

    private final int maxConcurrentCalls;
    private final int failureThreshold;
    private final long openNanos;
    private final long queueWaitMillis;

    private final Semaphore permits;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();

    /** 熔断截止时间（{@link System#nanoTime()} 基准）；0 表示未熔断。 */
    private volatile long openUntilNanos;

    public AiCallGuard(AiProperties properties) {
        this.maxConcurrentCalls = Math.max(1, properties.getMaxConcurrentCalls());
        this.failureThreshold = Math.max(0, properties.getBreakerFailureThreshold());
        this.openNanos = Math.max(0L, properties.getBreakerOpenDuration().toNanos());
        this.queueWaitMillis = Math.max(0L, properties.getQueueWait().toMillis());
        this.permits = new Semaphore(maxConcurrentCalls, true);
    }

    /**
     * 申请一次调用名额；熔断中或并发已满时抛 {@link AiUnavailableException}，调用方立即降级。
     */
    public Permit acquire() {
        throwIfOpen();
        boolean acquired;
        try {
            acquired = queueWaitMillis <= 0
                    ? permits.tryAcquire()
                    : permits.tryAcquire(queueWaitMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AiUnavailableException("AI 调用等待名额被中断", exception);
        }
        if (!acquired) {
            throw new AiUnavailableException("AI 并发已满（上限 " + maxConcurrentCalls + "），降级处理");
        }
        // 等名额期间可能刚好熔断：二次确认，避免放行一次注定失败的调用。
        try {
            throwIfOpen();
        } catch (RuntimeException exception) {
            permits.release();
            throw exception;
        }
        return new Permit();
    }

    public boolean isOpen() {
        long until = openUntilNanos;
        return until != 0L && System.nanoTime() - until < 0;
    }

    private void throwIfOpen() {
        if (isOpen()) {
            throw new AiUnavailableException("AI 连续失败已熔断，降级处理");
        }
    }

    private void onSuccess() {
        if (consecutiveFailures.getAndSet(0) != 0) {
            log.info("AI 调用恢复，失败计数清零");
        }
    }

    private void onFailure() {
        if (failureThreshold == 0) {
            return;
        }
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= failureThreshold && openNanos > 0) {
            consecutiveFailures.set(0);
            openUntilNanos = System.nanoTime() + openNanos;
            log.warn("AI 连续失败 {} 次，熔断 {}ms", failures, openNanos / 1_000_000);
        }
    }

    /**
     * 一次调用的名额；必须 try-with-resources 使用。
     *
     * <p>标记语义：{@code success} 清零失败计数，{@code failure} 计入熔断，
     * 两者都不标记表示"这次失败与上游可用性无关"（如请求参数错误），只归还名额。</p>
     */
    public final class Permit implements AutoCloseable {

        private boolean success;
        private boolean failure;
        private boolean closed;

        private Permit() {}

        public void success() {
            this.success = true;
        }

        public void failure() {
            this.failure = true;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            permits.release();
            if (failure) {
                onFailure();
            } else if (success) {
                onSuccess();
            }
        }
    }
}
