package com.offercoach.interview.report.application;

import com.offercoach.common.trace.JobTrace;
import com.offercoach.interview.ai.InterviewMetrics;
import com.offercoach.interview.config.InterviewProperties;
import com.offercoach.interview.report.application.port.InterviewReportRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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
    private final InterviewMetrics metrics;
    private final Set<Long> running = ConcurrentHashMap.newKeySet();

    public ReportGenerationWorker(
            InterviewReportRepository reportRepository,
            ReportService reportService,
            InterviewProperties properties,
            @Qualifier("aiExecutor") Executor aiExecutor,
            InterviewMetrics metrics) {
        this.reportRepository = reportRepository;
        this.reportService = reportService;
        this.properties = properties;
        this.aiExecutor = aiExecutor;
        this.metrics = metrics;
    }

    @Scheduled(fixedDelay = 5000L)
    public void run() {
        try (JobTrace ignored = JobTrace.open("report-worker")) {
            List<Long> pending = reportRepository.findStaleGenerating(STALE_SECONDS, BATCH_SIZE);
            for (Long reportId : pending) {
                claimAndSubmit(reportId, STALE_SECONDS);
            }
        }
    }

    /**
     * 立即触发一次生成；进程内去重，跨实例靠租约 token 与 claim 条件更新。
     * 触发失败只记录日志：会话/报告状态已提交，5 秒轮询会兜底，不应把接口变成 500。
     */
    public void kick(long reportId) {
        try {
            claimAndSubmit(reportId, 0);
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

    /**
     * 先占进程内名额再 claim，二者顺序不能反。
     *
     * <p>{@code claim} 会覆盖 {@code generation_token}；若在 claim 之后才发现本进程已有任务在跑
     * （旧任务的 token 已被覆盖），旧任务完成时 {@code ReportService.generate} 会因租约不匹配
     * 静默丢弃结果，报告就永久停在 GENERATING，并被下一轮再次 claim——活锁。</p>
     */
    private void claimAndSubmit(long reportId, int staleSeconds) {
        if (!running.add(reportId)) {
            return;
        }
        boolean claimed = false;
        try {
            String token = UUID.randomUUID().toString();
            claimed = reportRepository.claim(reportId, staleSeconds, token);
            if (!claimed) {
                return;
            }
            String submitted = token;
            aiExecutor.execute(() -> {
                try {
                    generate(reportId, submitted);
                } finally {
                    running.remove(reportId);
                }
            });
        } catch (RuntimeException exception) {
            log.warn(
                    "报告任务提交失败：reportId={} reason={}",
                    reportId,
                    exception.getClass().getSimpleName());
        } finally {
            if (!claimed) {
                running.remove(reportId);
            }
        }
    }

    private void generate(long reportId, String token) {
        long startedAt = System.nanoTime();
        try {
            reportService.generate(reportId, token);
            metrics.reportGeneration("success", (System.nanoTime() - startedAt) / 1_000_000);
        } catch (ReportService.ScoringPendingException exception) {
            metrics.reportGeneration("pending", (System.nanoTime() - startedAt) / 1_000_000);
            log.info("评分尚未完成，等待下次调度：reportId={}", reportId);
        } catch (ReportService.EmptyFeedbackException exception) {
            metrics.reportGeneration("empty", (System.nanoTime() - startedAt) / 1_000_000);
            log.warn("报告没有可聚合的评分，直接终止：reportId={}", reportId);
            reportService.failFinal(reportId, token, "NO_SCORABLE_FEEDBACK");
        } catch (RuntimeException exception) {
            metrics.reportGeneration("failed", (System.nanoTime() - startedAt) / 1_000_000);
            String reason = exception.getClass().getSimpleName();
            log.warn("报告生成失败：reportId={} reason={}", reportId, reason);
            reportService.fail(reportId, token, truncate(reason, 64), properties.getReportMaxAttempts());
        }
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
