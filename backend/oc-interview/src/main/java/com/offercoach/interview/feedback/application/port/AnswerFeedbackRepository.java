package com.offercoach.interview.feedback.application.port;

import com.offercoach.interview.feedback.domain.AnswerFeedback;
import java.util.List;
import java.util.Optional;

/**
 * 反馈仓储端口；requestId 与 (answer_id, version) 均唯一，重复写入按幂等忽略。
 */
public interface AnswerFeedbackRepository {

    Optional<AnswerFeedback> findLatestByAnswer(long userId, long answerId);

    Optional<AnswerFeedback> findByRequestId(String requestId);

    int insertIfAbsent(AnswerFeedback feedback);

    List<AnswerFeedback> listBySession(long sessionId);
}
