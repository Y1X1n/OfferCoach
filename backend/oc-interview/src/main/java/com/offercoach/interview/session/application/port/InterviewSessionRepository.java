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

    void advanceAfterAnswer(long id);

    void advanceAfterSkip(long id);

    List<InterviewSession> findIdle(OffsetDateTime cutoff, int limit);

    /**
     * 看门狗：超时仍停留在 PREPARING 的会话。
     */
    List<InterviewSession> findStalePreparing(OffsetDateTime cutoff, int limit);

    boolean markIdleAbandoned(long id, OffsetDateTime cutoff);
}
