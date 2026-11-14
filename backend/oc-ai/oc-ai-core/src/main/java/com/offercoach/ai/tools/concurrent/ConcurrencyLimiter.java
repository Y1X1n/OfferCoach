package com.offercoach.ai.tools.concurrent;

import com.offercoach.ai.tools.error.ToolErrorKind;
import com.offercoach.ai.tools.error.ToolException;
import java.util.concurrent.Semaphore;

/**
 * 在途请求限流：超出上限立即失败而非排队，避免长耗时请求堆积拖垮进程。
 *
 * <p>上限应对齐上游账号级并发配额，留出安全余量。</p>
 */
public final class ConcurrencyLimiter {

    private final Semaphore semaphore;
    private final String name;

    public ConcurrencyLimiter(String name, int permits) {
        this.name = name;
        this.semaphore = new Semaphore(Math.max(1, permits), true);
    }

    public Permit acquire() {
        if (!semaphore.tryAcquire()) {
            throw new ToolException(ToolErrorKind.BUSY, name + " 并发已达上限");
        }
        return new Permit(semaphore);
    }

    /**
     * 许可；必须在 try-with-resources 中使用，确保异常路径也释放。
     */
    public record Permit(Semaphore semaphore) implements AutoCloseable {

        @Override
        public void close() {
            semaphore.release();
        }
    }
}
