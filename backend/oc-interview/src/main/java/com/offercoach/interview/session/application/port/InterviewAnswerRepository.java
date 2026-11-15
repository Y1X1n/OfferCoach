package com.offercoach.interview.session.application.port;

import com.offercoach.interview.session.domain.InterviewAnswer;
import java.util.List;
import java.util.Optional;

/**
 * 回答仓储端口；每题唯一回答由 interview_answer.question_id 唯一约束保证。
 */
public interface InterviewAnswerRepository {

    long insert(InterviewAnswer answer);

    Optional<InterviewAnswer> findOwned(long userId, long sessionId, long answerId);

    Optional<InterviewAnswer> findByQuestionId(long questionId);

    Optional<InterviewAnswer> findOwnedByAnswer(long userId, long answerId);

    long countBySession(long sessionId);

    List<InterviewAnswer> listBySession(long sessionId);

    void markScored(long answerId);

    /**
     * 看门狗：把超时仍为 SCORING 的回答标记失败。
     */
    int failStaleScoring(java.time.OffsetDateTime cutoff);
}
