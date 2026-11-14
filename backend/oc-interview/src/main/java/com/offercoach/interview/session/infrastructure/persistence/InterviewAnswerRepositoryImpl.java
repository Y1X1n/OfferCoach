package com.offercoach.interview.session.infrastructure.persistence;

import com.offercoach.interview.session.application.port.InterviewAnswerRepository;
import com.offercoach.interview.session.domain.InterviewAnswer;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 回答仓储实现。
 */
@Repository
@RequiredArgsConstructor
public class InterviewAnswerRepositoryImpl implements InterviewAnswerRepository {

    private final InterviewAnswerMapper answerMapper;

    @Override
    public long insert(InterviewAnswer answer) {
        InterviewAnswerDO row = new InterviewAnswerDO();
        row.setSessionId(answer.sessionId());
        row.setQuestionId(answer.questionId());
        row.setUserId(answer.userId());
        row.setInputMode(answer.inputMode());
        row.setAnswerText(answer.answerText());
        row.setDurationMs(answer.durationMs());
        row.setStatus(answer.status());
        row.setAudioKey(answer.audioKey());
        row.setAudioDurationMs(answer.audioKey() == null ? null : answer.durationMs());
        row.setAsrStatus(answer.asrStatus());
        answerMapper.insert(row);
        return row.getId() == null ? 0 : row.getId();
    }

    @Override
    public Optional<InterviewAnswer> findOwned(long userId, long sessionId, long answerId) {
        return Optional.ofNullable(toAnswer(answerMapper.findOwned(userId, sessionId, answerId)));
    }

    @Override
    public Optional<InterviewAnswer> findByQuestionId(long questionId) {
        return Optional.ofNullable(toAnswer(answerMapper.findByQuestionId(questionId)));
    }

    @Override
    public Optional<InterviewAnswer> findOwnedByAnswer(long userId, long answerId) {
        return Optional.ofNullable(toAnswer(answerMapper.findOwnedByAnswer(userId, answerId)));
    }

    @Override
    public long countBySession(long sessionId) {
        return answerMapper.countBySession(sessionId);
    }

    @Override
    public List<InterviewAnswer> listBySession(long sessionId) {
        return answerMapper.listBySession(sessionId).stream()
                .map(InterviewAnswerRepositoryImpl::toAnswer)
                .toList();
    }

    @Override
    public void markScored(long answerId) {
        answerMapper.markScored(answerId);
    }

    @Override
    public void updateAsrStatus(long answerId, String asrStatus) {
        answerMapper.updateAsrStatus(answerId, asrStatus);
    }

    @Override
    public int failStaleScoring(java.time.OffsetDateTime cutoff) {
        return answerMapper.failStaleScoring(cutoff);
    }

    private static InterviewAnswer toAnswer(InterviewAnswerDO row) {
        if (row == null) {
            return null;
        }
        return new InterviewAnswer(
                row.getId() == null ? 0 : row.getId(),
                row.getSessionId() == null ? 0 : row.getSessionId(),
                row.getQuestionId() == null ? 0 : row.getQuestionId(),
                row.getUserId() == null ? 0 : row.getUserId(),
                row.getInputMode(),
                row.getAnswerText(),
                row.getDurationMs(),
                row.getStatus(),
                row.getSubmittedAt(),
                row.getAudioKey(),
                row.getAsrStatus());
    }
}
