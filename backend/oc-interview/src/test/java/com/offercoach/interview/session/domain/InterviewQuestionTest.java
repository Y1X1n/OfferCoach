package com.offercoach.interview.session.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 追问可答性：追问追加在题尾，但不能被 currentSeq 顺序门禁挡住。
 */
class InterviewQuestionTest {

    @Test
    void bankQuestionIsAnswerableOnlyUpToCurrentSeq() {
        InterviewQuestion question = question(5, "BANK");

        assertThat(question.isAnswerableAt(5)).isTrue();
        assertThat(question.isAnswerableAt(6)).isTrue();
        assertThat(question.isAnswerableAt(4)).isFalse();
    }

    @Test
    void followUpIsAnswerableEvenAtTailSeq() {
        InterviewQuestion followUp = question(9, "FOLLOW_UP");

        assertThat(followUp.isFollowUp()).isTrue();
        assertThat(followUp.isAnswerableAt(4)).isTrue();
        assertThat(followUp.isAnswerableAt(1)).isTrue();
    }

    private static InterviewQuestion question(int seq, String source) {
        return new InterviewQuestion(
                1L, 100L, seq, "DEPTH", "MEDIUM", "题目", source, null, List.of(), "", "{}", "{}", "PENDING");
    }
}
