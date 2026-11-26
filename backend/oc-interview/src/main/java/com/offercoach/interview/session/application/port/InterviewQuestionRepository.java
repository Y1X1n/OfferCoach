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

    /** 会话内已有的追问数量；追问上限为 2。 */
    long countFollowUps(long sessionId);

    /** 当前最大 seq；新追问使用 maxSeq + 1。 */
    int maxSeq(long sessionId);

    /**
     * 追加一道追问；parentQuestionId 指向被追问的原题，source 固定 FOLLOW_UP。
     */
    long insertFollowUp(
            long sessionId,
            int seq,
            String dimension,
            String difficulty,
            String text,
            List<String> referencePoints,
            String whyThisQuestion,
            long parentQuestionId);
}
