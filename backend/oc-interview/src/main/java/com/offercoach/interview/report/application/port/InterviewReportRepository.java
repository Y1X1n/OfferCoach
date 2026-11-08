package com.offercoach.interview.report.application.port;

import com.offercoach.interview.report.domain.InterviewReport;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 报告仓储端口；生成抢占依赖 status/updated_at 行级条件更新。
 */
public interface InterviewReportRepository {

    long insertIfAbsent(long sessionId, long userId);

    Optional<InterviewReport> findOwned(long userId, long id);

    Optional<InterviewReport> findById(long id);

    Optional<InterviewReport> findBySession(long sessionId);

    List<InterviewReport> list(long userId, int offset, int limit);

    long count(long userId);

    List<Long> findStaleGenerating(int staleSeconds, int limit);

    boolean claim(long id, int staleSeconds);

    void markReady(
            long id,
            BigDecimal overallScore,
            String radarJson,
            String summary,
            String highlightsJson,
            String risksJson,
            String improvementPlanJson,
            String questionBreakdownJson);

    /** 失败重试计数：返回自增后的 attempts，行不存在或非 GENERATING 时返回 0。 */
    int incrementAttempts(long id, String lastError);

    void markFailed(long id, String lastError);

    boolean resetForRetry(long userId, long id);
}
