package com.offercoach.interview.session.domain;

import java.util.List;

/**
 * 面试题目领域视图；referencePoints 由 JSONB 解析为字符串列表。
 */
public record InterviewQuestion(
        long id,
        long sessionId,
        int seq,
        String dimension,
        String difficulty,
        String text,
        String source,
        Long bankQuestionId,
        List<String> referencePoints,
        String whyThisQuestion,
        String retrievalJson,
        String contextJson,
        String status) {

    private static final String SOURCE_FOLLOW_UP = "FOLLOW_UP";

    public boolean isFollowUp() {
        return SOURCE_FOLLOW_UP.equals(source);
    }

    /**
     * 追问追加在题尾，但用户是在当前原题后立即回答，不能受 currentSeq 顺序门禁限制。
     */
    public boolean isAnswerableAt(int currentSeq) {
        return seq <= currentSeq || isFollowUp();
    }
}
