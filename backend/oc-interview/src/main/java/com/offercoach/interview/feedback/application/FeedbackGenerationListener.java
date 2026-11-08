package com.offercoach.interview.feedback.application;

import com.offercoach.interview.report.application.ReportGenerationWorker;
import com.offercoach.interview.session.application.AnswerSubmitted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 回答事务提交后预生成反馈；前端随后轮询反馈接口，不再依赖 SSE。
 * 若会话已结束并等待报告，评分落库后立即触发报告聚合。
 */
@Component
public class FeedbackGenerationListener {

    private static final Logger log = LoggerFactory.getLogger(FeedbackGenerationListener.class);

    private final FeedbackService feedbackService;
    private final ReportGenerationWorker reportGenerationWorker;

    public FeedbackGenerationListener(FeedbackService feedbackService, ReportGenerationWorker reportGenerationWorker) {
        this.feedbackService = feedbackService;
        this.reportGenerationWorker = reportGenerationWorker;
    }

    @Async("aiExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSubmitted(AnswerSubmitted event) {
        try {
            FeedbackService.Prepared prepared = feedbackService.prepare(event.userId(), event.answerId());
            reportGenerationWorker.kickBySession(prepared.session().id());
        } catch (RuntimeException exception) {
            log.warn(
                    "异步反馈预生成失败：answerId={} reason={}",
                    event.answerId(),
                    exception.getClass().getSimpleName());
        }
    }
}
