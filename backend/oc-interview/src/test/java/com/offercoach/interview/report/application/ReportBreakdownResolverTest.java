package com.offercoach.interview.report.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.offercoach.interview.feedback.application.port.AnswerFeedbackRepository;
import com.offercoach.interview.feedback.domain.AnswerFeedback;
import com.offercoach.interview.session.application.port.InterviewAnswerRepository;
import com.offercoach.interview.session.application.port.InterviewQuestionRepository;
import com.offercoach.interview.session.domain.InterviewAnswer;
import com.offercoach.interview.session.domain.InterviewQuestion;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 报告详情素材必须按会话批量取：逐题查时每个 breakdown 项三条 SQL，20 题 ≈ 60 条。
 */
class ReportBreakdownResolverTest {

    private static final long USER_ID = 7L;
    private static final long SESSION_ID = 100L;

    private InterviewQuestionRepository questionRepository;
    private InterviewAnswerRepository answerRepository;
    private AnswerFeedbackRepository feedbackRepository;
    private ReportBreakdownResolver resolver;

    @BeforeEach
    void setUp() {
        questionRepository = mock(InterviewQuestionRepository.class);
        answerRepository = mock(InterviewAnswerRepository.class);
        feedbackRepository = mock(AnswerFeedbackRepository.class);
        resolver = new ReportBreakdownResolver(questionRepository, answerRepository, feedbackRepository);
    }

    @Test
    void resolvesWholeReportWithThreeQueries() {
        when(questionRepository.listBySession(SESSION_ID))
                .thenReturn(List.of(question(11L, "介绍一下你的项目"), question(12L, "如何做限流")));
        when(answerRepository.listOwnedBySession(USER_ID, SESSION_ID))
                .thenReturn(List.of(answer(21L, 11L, "做了个订单系统"), answer(22L, 12L, "令牌桶加滑动窗口")));
        when(feedbackRepository.listLatestBySession(USER_ID, SESSION_ID))
                .thenReturn(List.of(feedback(31L, 21L, 2), feedback(32L, 22L, 1)));

        ReportBreakdownResolver.Sources sources = resolver.resolve(USER_ID, SESSION_ID);

        assertThat(sources.questionText(11L)).isEqualTo("介绍一下你的项目");
        assertThat(sources.answerText(21L)).isEqualTo("做了个订单系统");
        assertThat(sources.feedback(21L).id()).isEqualTo(31L);

        // 关键断言：三张表各只查一次，且不再逐题 findOwned / findLatestByAnswer
        verify(questionRepository).listBySession(SESSION_ID);
        verify(answerRepository).listOwnedBySession(USER_ID, SESSION_ID);
        verify(feedbackRepository).listLatestBySession(USER_ID, SESSION_ID);
        verify(questionRepository, never()).findOwned(anyLong(), anyLong());
        verify(answerRepository, never()).findOwned(anyLong(), anyLong(), anyLong());
        verify(feedbackRepository, never()).findLatestByAnswer(anyLong(), anyLong());
    }

    @Test
    void emptySessionIssuesNoQueries() {
        when(questionRepository.listBySession(SESSION_ID)).thenReturn(List.of());
        when(answerRepository.listOwnedBySession(USER_ID, SESSION_ID)).thenReturn(List.of());
        when(feedbackRepository.listLatestBySession(USER_ID, SESSION_ID)).thenReturn(List.of());

        ReportBreakdownResolver.Sources sources = resolver.resolve(USER_ID, SESSION_ID);

        assertThat(sources.questionText(11L)).isNull();
        assertThat(sources.answerText(21L)).isNull();
        assertThat(sources.feedback(21L)).isNull();
    }

    /**
     * 报告可能早于回答/反馈被清理（或反馈被重试覆盖）：缺失项返回 null，让接口继续出报告，
     * 不要 500。
     */
    @Test
    void missingAnswerOrFeedbackYieldsNullInsteadOfFailing() {
        when(questionRepository.listBySession(SESSION_ID)).thenReturn(List.of(question(11L, "题面")));
        when(answerRepository.listOwnedBySession(USER_ID, SESSION_ID)).thenReturn(List.of());
        when(feedbackRepository.listLatestBySession(USER_ID, SESSION_ID)).thenReturn(List.of());

        ReportBreakdownResolver.Sources sources = resolver.resolve(USER_ID, SESSION_ID);

        assertThat(sources.questionText(11L)).isEqualTo("题面");
        assertThat(sources.answerText(21L)).isNull();
        assertThat(sources.feedback(21L)).isNull();
    }

    private static InterviewQuestion question(long id, String text) {
        return new InterviewQuestion(
                id, SESSION_ID, (int) id, "TECH", "MEDIUM", text, "BANK", null, List.of(), null, null, null, "ASKED");
    }

    private static InterviewAnswer answer(long id, long questionId, String text) {
        return new InterviewAnswer(
                id, SESSION_ID, questionId, USER_ID, "TEXT", text, 1000, "SCORED", OffsetDateTime.now(), null, null);
    }

    private static AnswerFeedback feedback(long id, long answerId, int version) {
        return new AnswerFeedback(
                id,
                answerId,
                SESSION_ID,
                USER_ID,
                version,
                "req-" + id,
                new BigDecimal("7.5"),
                Map.of(),
                List.of(),
                List.of(),
                List.of(),
                "改写稿",
                "model",
                "provider",
                false,
                OffsetDateTime.now());
    }
}
