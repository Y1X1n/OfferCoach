package com.offercoach.interview.session.application.port;

import com.offercoach.interview.session.domain.InterviewSession;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 会话仓储端口；所有按用户读取的方法必须带 user_id 归属条件。
 */
public interface InterviewSessionRepository {

    Optional<InterviewSession> findOwned(long userId, long id);

    Optional<InterviewSession> findAny(long id);

    /**
     * 按 id 批量取会话（归属过滤），用于报告列表一次查完，避免逐条会话查询。
     */
    List<InterviewSession> findByIdsOwned(long userId, java.util.Collection<Long> ids);

    /** 最近活跃的未结束会话（CREATED/READY/IN_PROGRESS/PAUSED）。 */
    Optional<InterviewSession> findLatestOpen(long userId);

    Optional<InterviewSession> findByUserAndKey(long userId, String idempotencyKey);

    long insert(InterviewSession session);

    boolean existsActive(long userId);

    List<InterviewSession> list(long userId, int offset, int limit);

    long count(long userId);

    boolean markPreparing(long id);

    boolean markReady(long id);

    boolean markGenerationFailed(long id, String failureReason);

    boolean markInProgress(long id);

    boolean pause(long id);

    boolean resume(long id);

    boolean abandon(long id);

    boolean finish(long id);

    boolean complete(long id, BigDecimal totalScore);

    boolean markReportFailed(long id);

    boolean markReportGenerating(long id);

    void touch(long id);

    /** 追加追问后同步题量，保证进度与报告聚合口径一致。 */
    boolean incrementQuestionCount(long id);

    /**
     * 事务内锁定会话行（{@code SELECT … FOR UPDATE}）；返回会话是否存在。
     *
     * <p>追问上限检查与作答推进都依赖它串行化：无锁时并发请求会同时通过上限判断。</p>
     */
    boolean lockForUpdate(long id);

    boolean advanceAfterAnswer(long id);

    boolean advanceAfterSkip(long id);

    List<InterviewSession> findIdle(OffsetDateTime cutoff, int limit);

    /**
     * 看门狗：超时仍停留在 PREPARING 的会话。
     */
    List<InterviewSession> findStalePreparing(OffsetDateTime cutoff, int limit);

    boolean markIdleAbandoned(long id, OffsetDateTime cutoff);
}
