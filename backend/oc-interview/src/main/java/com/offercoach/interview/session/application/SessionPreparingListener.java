package com.offercoach.interview.session.application;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 会话置 PREPARING 的事务提交后再异步出题，避免异步线程读不到未提交数据。
 */
@Component
public class SessionPreparingListener {

    private final QuestionGenerationWorker questionGenerationWorker;

    public SessionPreparingListener(QuestionGenerationWorker questionGenerationWorker) {
        this.questionGenerationWorker = questionGenerationWorker;
    }

    @Async("aiExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPreparing(SessionPreparingRequested event) {
        questionGenerationWorker.generate(event.sessionId());
    }
}
