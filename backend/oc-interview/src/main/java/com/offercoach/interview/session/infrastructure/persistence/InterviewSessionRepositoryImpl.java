package com.offercoach.interview.session.infrastructure.persistence;

import com.offercoach.interview.session.application.port.InterviewSessionRepository;
import com.offercoach.interview.session.domain.InterviewSession;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 会话仓储实现：DO 与领域 record 在边界互相映射。
 */
@Repository
@RequiredArgsConstructor
public class InterviewSessionRepositoryImpl implements InterviewSessionRepository {

    private final InterviewSessionMapper sessionMapper;

    @Override
    public Optional<InterviewSession> findOwned(long userId, long id) {
        return Optional.ofNullable(toSession(sessionMapper.findOwned(userId, id)));
    }

    @Override
    public Optional<InterviewSession> findAny(long id) {
        return Optional.ofNullable(toSession(sessionMapper.findAny(id)));
    }

    @Override
    public Optional<InterviewSession> findLatestOpen(long userId) {
        return Optional.ofNullable(toSession(sessionMapper.findLatestOpen(userId)));
    }

    @Override
    public Optional<InterviewSession> findByUserAndKey(long userId, String idempotencyKey) {
        return Optional.ofNullable(toSession(sessionMapper.findByUserAndKey(userId, idempotencyKey)));
    }

    @Override
    public long insert(InterviewSession session) {
        InterviewSessionDO row = new InterviewSessionDO();
        row.setUserId(session.userId());
        row.setResumeId(session.resumeId());
        row.setJdId(session.jdId());
        row.setStatus(session.status());
        row.setConfigJson(session.configJson());
        row.setQuestionCount(session.questionCount());
        row.setIdempotencyKey(session.idempotencyKey());
        sessionMapper.insert(row);
        return row.getId() == null ? 0 : row.getId();
    }

    @Override
    public boolean existsActive(long userId) {
        return sessionMapper.countActive(userId) > 0;
    }

    @Override
    public List<InterviewSession> list(long userId, int offset, int limit) {
        return sessionMapper.list(userId, offset, limit).stream()
                .map(InterviewSessionRepositoryImpl::toSession)
                .toList();
    }

    @Override
    public long count(long userId) {
        return sessionMapper.count(userId);
    }

    @Override
    public boolean markPreparing(long id) {
        return sessionMapper.markPreparing(id) > 0;
    }

    @Override
    public boolean markReady(long id) {
        return sessionMapper.markReady(id) > 0;
    }

    @Override
    public boolean markGenerationFailed(long id, String failureReason) {
        return sessionMapper.markGenerationFailed(id, failureReason) > 0;
    }

    @Override
    public boolean markInProgress(long id) {
        return sessionMapper.markInProgress(id) > 0;
    }

    @Override
    public boolean pause(long id) {
        return sessionMapper.pause(id) > 0;
    }

    @Override
    public boolean resume(long id) {
        return sessionMapper.resume(id) > 0;
    }

    @Override
    public boolean abandon(long id) {
        return sessionMapper.abandon(id) > 0;
    }

    @Override
    public boolean finish(long id) {
        return sessionMapper.finish(id) > 0;
    }

    @Override
    public boolean complete(long id, BigDecimal totalScore) {
        return sessionMapper.complete(id, totalScore) > 0;
    }

    @Override
    public boolean markReportFailed(long id) {
        return sessionMapper.markReportFailed(id) > 0;
    }

    @Override
    public boolean markReportGenerating(long id) {
        return sessionMapper.markReportGenerating(id) > 0;
    }

    @Override
    public void touch(long id) {
        sessionMapper.touch(id);
    }

    @Override
    public boolean advanceAfterAnswer(long id) {
        return sessionMapper.advanceAfterAnswer(id) > 0;
    }

    @Override
    public boolean advanceAfterSkip(long id) {
        return sessionMapper.advanceAfterSkip(id) > 0;
    }

    @Override
    public List<InterviewSession> findIdle(OffsetDateTime cutoff, int limit) {
        return sessionMapper.findIdle(cutoff, limit).stream()
                .map(InterviewSessionRepositoryImpl::toSession)
                .toList();
    }

    @Override
    public List<InterviewSession> findStalePreparing(OffsetDateTime cutoff, int limit) {
        return sessionMapper.findStalePreparing(cutoff, limit).stream()
                .map(InterviewSessionRepositoryImpl::toSession)
                .toList();
    }

    @Override
    public boolean markIdleAbandoned(long id, OffsetDateTime cutoff) {
        return sessionMapper.markIdleAbandoned(id, cutoff) > 0;
    }

    private static InterviewSession toSession(InterviewSessionDO row) {
        if (row == null) {
            return null;
        }
        return new InterviewSession(
                row.getId() == null ? 0 : row.getId(),
                row.getUserId() == null ? 0 : row.getUserId(),
                row.getResumeId() == null ? 0 : row.getResumeId(),
                row.getJdId() == null ? 0 : row.getJdId(),
                row.getStatus(),
                row.getConfigJson(),
                row.getQuestionCount() == null ? 0 : row.getQuestionCount(),
                row.getAnsweredCount() == null ? 0 : row.getAnsweredCount(),
                row.getCurrentSeq() == null ? 1 : row.getCurrentSeq(),
                row.getTotalScore(),
                row.getFailureReason(),
                row.getIdempotencyKey(),
                row.getStartedAt(),
                row.getFinishedAt(),
                row.getLastActiveAt(),
                row.getCreatedAt());
    }
}
