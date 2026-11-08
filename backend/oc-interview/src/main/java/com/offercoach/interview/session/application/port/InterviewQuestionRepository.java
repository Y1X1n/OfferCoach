package com.offercoach.interview.session.application.port;

import com.offercoach.interview.session.domain.InterviewQuestion;
import java.util.List;
import java.util.Optional;

/**
 * 面试题目仓储端口。
 */
public interface InterviewQuestionRepository {

    long insert(InterviewQuestion question);

    List<InterviewQuestion> listBySession(long sessionId);

    List<InterviewQuestion> listVisible(long sessionId, int currentSeq);

    Optional<InterviewQuestion> findOwned(long sessionId, long questionId);

    long countBySession(long sessionId);

    long countByStatus(long sessionId, String status);

    boolean markAsked(long sessionId, int seq);

    boolean markAnswered(long questionId);

    boolean markSkipped(long questionId);
}
