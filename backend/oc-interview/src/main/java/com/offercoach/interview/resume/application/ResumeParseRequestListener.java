package com.offercoach.interview.resume.application;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 事务提交后异步触发解析，确保 worker 能读到已提交的 PENDING 行。
 */
@Component
public class ResumeParseRequestListener {

    private final ResumeParseExecutor executor;

    public ResumeParseRequestListener(ResumeParseExecutor executor) {
        this.executor = executor;
    }

    @Async("aiExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onRequested(ResumeParseRequested event) {
        executor.execute(event.resumeId());
    }
}
