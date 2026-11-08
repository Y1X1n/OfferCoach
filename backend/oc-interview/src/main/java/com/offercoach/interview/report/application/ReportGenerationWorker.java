package com.offercoach.interview.report.application;

import com.offercoach.interview.config.InterviewProperties;
import com.offercoach.interview.report.application.port.InterviewReportRepository;
import java.util.List;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 报告 worker：每 5 秒领取超过 5 秒未更新的 GENERATING 报告，
 * 领取后交给 AI 线程池生成，避免长时间占用调度线程。
 */
@Service
public class ReportGenerationWorker {

    private static final Logger log = LoggerFactory.getLogger(ReportGenerationWorker.class);
    private static final int STALE_SECONDS = 5;
    private static final int BATCH_SIZE = 5;

    private final InterviewReportRepository reportRepository;
    private final ReportService reportService;
    private final InterviewProperties properties;
    private final Executor aiExecutor;

    public ReportGenerationWorker(
            InterviewReportRepository reportRepository,
            ReportService reportService,
            InterviewProperties properties,
            @Qualifier("aiExecutor") Executor aiExecutor) {
        this.reportRepository = reportRepository;
        this.reportService = reportService;
        this.properties = properties;
        this.aiExecutor = aiExecutor;
    }

    @Scheduled(fixedDelay = 5000L)
    public void run() {
        List<Long> pending = reportRepository.findStaleGenerating(STALE_SECONDS, BATCH_SIZE);
        for (Long reportId : pending) {
            if (!reportRepository.claim(reportId, STALE_SECONDS)) {
                continue;
            }
            aiExecutor.execute(() -> generate(reportId));
        }
    }

    private void generate(long reportId) {
        try {
            reportService.generate(reportId);
        } catch (RuntimeException exception) {
            String reason = exception.getClass().getSimpleName();
            log.warn("报告生成失败：reportId={} reason={}", reportId, reason);
            reportService.fail(reportId, truncate(reason, 64), properties.getReportMaxAttempts());
        }
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
