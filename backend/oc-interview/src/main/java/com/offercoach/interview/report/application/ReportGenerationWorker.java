package com.offercoach.interview.report.application;

import com.offercoach.interview.config.InterviewProperties;
import com.offercoach.interview.report.application.port.InterviewReportRepository;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 报告 worker：结束会话、评分完成与手动重试时立即触发；每 5 秒扫描超过 5 秒未更新的
 * GENERATING 报告作为兜底。领取后交给 AI 线程池生成，避免长时间占用调度线程。
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
    private final Set<Long> running = ConcurrentHashMap.newKeySet();

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
            if (reportRepository.claim(reportId, STALE_SECONDS)) {
                submit(reportId);
            }
        }
    }

    /**
     * 立即触发一次生成；进程内去重，跨实例靠数据库 claim 条件更新。
     * 触发失败只记录日志：会话/报告状态已提交，5 秒轮询会兜底，不应把接口变成 500。
     */
    public void kick(long reportId) {
        if (running.contains(reportId)) {
            return;
        }
        try {
            if (reportRepository.claim(reportId, 0)) {
                submit(reportId);
            }
        } catch (RuntimeException exception) {
            log.warn(
                    "报告即时触发失败，等待轮询：reportId={} reason={}",
                    reportId,
                    exception.getClass().getSimpleName());
        }
    }

    /**
     * 按会话触发：仅当报告仍为 GENERATING 时执行。
     */
    public void kickBySession(long sessionId) {
        reportRepository
                .findBySession(sessionId)
                .filter(report -> "GENERATING".equals(report.status()))
                .ifPresent(report -> kick(report.id()));
    }

    private void submit(long reportId) {
        if (!running.add(reportId)) {
            return;
        }
        try {
            aiExecutor.execute(() -> {
                try {
                    generate(reportId);
                } finally {
                    running.remove(reportId);
                }
            });
        } catch (RuntimeException exception) {
            running.remove(reportId);
            log.warn(
                    "报告任务提交失败：reportId={} reason={}",
                    reportId,
                    exception.getClass().getSimpleName());
        }
    }

    private void generate(long reportId) {
        try {
            reportService.generate(reportId);
        } catch (ReportService.ScoringPendingException exception) {
            log.info("评分尚未完成，等待下次调度：reportId={}", reportId);
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
