package com.offercoach.interview.session.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.offercoach.common.error.BizException;
import com.offercoach.common.error.ErrorCode;
import com.offercoach.interview.ai.AiCallLogger;
import com.offercoach.interview.ai.AiGateway;
import com.offercoach.interview.session.application.port.InterviewAnswerRepository;
import com.offercoach.interview.session.application.port.InterviewQuestionRepository;
import com.offercoach.interview.session.application.port.InterviewSessionRepository;
import com.offercoach.interview.session.domain.InterviewAnswer;
import com.offercoach.interview.session.domain.InterviewQuestion;
import com.offercoach.interview.session.domain.InterviewSession;
import com.offercoach.interview.session.domain.SessionStatus;
import com.offercoach.interview.support.JsonCodec;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class FollowUpServiceTest {

    @Mock
    private InterviewSessionRepository sessionRepository;

    @Mock
    private InterviewQuestionRepository questionRepository;

    @Mock
    private InterviewAnswerRepository answerRepository;

    @Mock
    private AiGateway aiGateway;

    @Mock
    private AiCallLogger aiCallLogger;

    @Spy
    private JsonCodec jsonCodec = new JsonCodec(new ObjectMapper());

    /** 单测不需要真实事务：让 execute 直接执行回调，等价于事务已提交。 */
    private final TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);

    @InjectMocks
    private FollowUpService service;

    /**
     * TransactionTemplate 是 final 方法，mock 无法拦截 execute；用真实模板会要求事务管理器。
     * 这里改为让 mock 直接返回回调结果，模拟「事务已提交」。
     */
    @BeforeEach
    void stubTransactionTemplate() {
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
    }

    @Test
    void appendsFallbackFollowUpAndIncrementsQuestionCount() {
        InterviewSession session = new InterviewSession(
                100L,
                7L,
                1L,
                2L,
                SessionStatus.IN_PROGRESS,
                "{}",
                8,
                1,
                2,
                null,
                null,
                "key",
                OffsetDateTime.now(),
                null,
                OffsetDateTime.now(),
                OffsetDateTime.now());
        InterviewQuestion parent = new InterviewQuestion(
                5L,
                100L,
                1,
                "DEPTH",
                "MEDIUM",
                "请介绍一次性能优化",
                "BANK",
                null,
                List.of("背景", "动作"),
                "关联 JD",
                "{}",
                "{}",
                "ANSWERED");
        InterviewAnswer answer = new InterviewAnswer(
                9L,
                100L,
                5L,
                7L,
                "TEXT",
                "我通过缓存把 P95 从 800ms 降到 120ms",
                null,
                "SCORED",
                OffsetDateTime.now(),
                null,
                null);
        when(sessionRepository.findOwned(7L, 100L)).thenReturn(Optional.of(session));
        when(questionRepository.findOwned(100L, 5L)).thenReturn(Optional.of(parent));
        when(answerRepository.findByQuestionId(5L)).thenReturn(Optional.of(answer));
        when(aiGateway.available()).thenReturn(false);
        when(sessionRepository.lockForUpdate(100L)).thenReturn(true);
        when(questionRepository.countFollowUps(100L)).thenReturn(0L);
        when(questionRepository.maxSeq(100L)).thenReturn(8);
        when(questionRepository.insertFollowUp(
                        eq(100L), eq(9), eq("DEPTH"), eq("MEDIUM"), anyString(), anyList(), anyString(), eq(5L)))
                .thenReturn(99L);
        when(sessionRepository.incrementQuestionCount(100L)).thenReturn(true);

        FollowUpService.FollowUpQuestion question = service.add(7L, 100L, 5L);

        assertThat(question.id()).isEqualTo(99L);
        assertThat(question.seq()).isEqualTo(9);
        assertThat(question.text()).contains("更具体的例子");
        verify(sessionRepository).incrementQuestionCount(100L);
        verify(aiCallLogger)
                .log(
                        eq(7L),
                        eq("QUESTION_GEN"),
                        eq("heuristic"),
                        eq("rules-v1"),
                        eq("DEGRADED"),
                        anyInt(),
                        anyInt(),
                        any(),
                        anyLong(),
                        any(),
                        eq(100L),
                        any());
    }

    @Test
    void concurrentInsertConflictBecomesStateConflict() {
        InterviewSession session = new InterviewSession(
                100L,
                7L,
                1L,
                2L,
                SessionStatus.IN_PROGRESS,
                "{}",
                8,
                1,
                2,
                null,
                null,
                "key",
                OffsetDateTime.now(),
                null,
                OffsetDateTime.now(),
                OffsetDateTime.now());
        InterviewQuestion parent = new InterviewQuestion(
                5L,
                100L,
                1,
                "DEPTH",
                "MEDIUM",
                "请介绍一次性能优化",
                "BANK",
                null,
                List.of("背景"),
                "关联 JD",
                "{}",
                "{}",
                "ANSWERED");
        InterviewAnswer answer = new InterviewAnswer(
                9L, 100L, 5L, 7L, "TEXT", "我把 P95 从 800ms 降到 120ms", null, "SCORED", OffsetDateTime.now(), null, null);
        when(sessionRepository.findOwned(7L, 100L)).thenReturn(Optional.of(session));
        when(questionRepository.findOwned(100L, 5L)).thenReturn(Optional.of(parent));
        when(answerRepository.findByQuestionId(5L)).thenReturn(Optional.of(answer));
        when(aiGateway.available()).thenReturn(false);
        when(sessionRepository.lockForUpdate(100L)).thenReturn(true);
        when(questionRepository.countFollowUps(100L)).thenReturn(0L);
        when(questionRepository.maxSeq(100L)).thenReturn(8);
        when(questionRepository.insertFollowUp(
                        anyLong(), anyInt(), anyString(), anyString(), anyString(), anyList(), anyString(), anyLong()))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> service.add(7L, 100L, 5L))
                .isInstanceOf(BizException.class)
                .extracting(exception -> ((BizException) exception).errorCode())
                .isEqualTo(ErrorCode.STATE_CONFLICT);
    }
}
