package com.offercoach.interview.session.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.offercoach.interview.config.InterviewProperties;
import com.offercoach.interview.session.application.port.InterviewAnswerRepository;
import com.offercoach.interview.session.application.port.InterviewQuestionRepository;
import com.offercoach.interview.session.application.port.InterviewSessionRepository;
import com.offercoach.interview.session.domain.InterviewAnswer;
import com.offercoach.interview.session.domain.InterviewQuestion;
import com.offercoach.interview.session.domain.InterviewSession;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * 提交回答时评分流令牌是"可选的实时通道"，不能让它的 Redis 故障把答案本身回滚掉。
 *
 * <p>原实现把令牌写入放在 {@code @Transactional} 内并抛异常，Redis 抖动时整个事务回滚，
 * 用户刚敲完的整段回答消失且需要重打。反馈本身可以用 {@code GET /answers/{id}/feedback}
 * 轮询拿到，SSE 也默认关闭。</p>
 */
class AnswerServiceTest {

    private static final long USER_ID = 1L;
    private static final long SESSION_ID = 2L;
    private static final long QUESTION_ID = 3L;
    private static final String ANSWER_TEXT = "这是一段足够长的回答，用来通过最小长度校验。";

    private InterviewSessionRepository sessionRepository;
    private InterviewQuestionRepository questionRepository;
    private InterviewAnswerRepository answerRepository;
    private ApplicationEventPublisher eventPublisher;
    private ValueOperations<String, String> valueOperations;
    private StringRedisTemplate redisTemplate;
    private AnswerService service;

    @BeforeEach
    void setUp() {
        sessionRepository = mock(InterviewSessionRepository.class);
        questionRepository = mock(InterviewQuestionRepository.class);
        answerRepository = mock(InterviewAnswerRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        valueOperations = mock(ValueOperations.class);
        redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        when(sessionRepository.findOwned(USER_ID, SESSION_ID)).thenReturn(Optional.of(session()));
        when(sessionRepository.lockForUpdate(SESSION_ID)).thenReturn(true);
        when(questionRepository.findOwned(SESSION_ID, QUESTION_ID)).thenReturn(Optional.of(question()));
        when(answerRepository.findByQuestionId(QUESTION_ID)).thenReturn(Optional.empty());
        when(answerRepository.insert(any())).thenReturn(11L);
        when(questionRepository.markAnswered(QUESTION_ID)).thenReturn(true);
        when(sessionRepository.advanceAfterAnswer(SESSION_ID)).thenReturn(true);

        service = new AnswerService(
                sessionRepository,
                questionRepository,
                answerRepository,
                new InterviewProperties(),
                redisTemplate,
                eventPublisher);
    }

    @Test
    void issuesStreamTokenWhenRedisWorks() {
        AnswerService.Submitted submitted =
                service.submit(USER_ID, SESSION_ID, QUESTION_ID, ANSWER_TEXT, 1000, null, null);

        assertThat(submitted.answerId()).isEqualTo(11L);
        assertThat(submitted.token()).isNotBlank();
        verify(eventPublisher).publishEvent(any(Object.class));
    }

    /**
     * 核心回归：Redis 写令牌失败时不能抛，答案要保住，只是不提供实时流。
     */
    @Test
    void keepsAnswerWhenStreamTokenWriteFails() {
        // ValueOperations.set(...) 返回 void，只能用 doThrow 打桩
        doThrow(new IllegalStateException("redis down"))
                .when(valueOperations)
                .set(anyString(), anyString(), any(Duration.class));

        AnswerService.Submitted submitted =
                service.submit(USER_ID, SESSION_ID, QUESTION_ID, ANSWER_TEXT, 1000, null, null);

        assertThat(submitted.answerId()).isEqualTo(11L);
        assertThat(submitted.status()).isEqualTo("SCORING");
        assertThat(submitted.token()).isNull();
        // 答案确实落库，事件照常发布（评分不依赖令牌）
        ArgumentCaptor<InterviewAnswer> saved = ArgumentCaptor.forClass(InterviewAnswer.class);
        verify(answerRepository).insert(saved.capture());
        assertThat(saved.getValue().answerText()).isEqualTo(ANSWER_TEXT);
        verify(eventPublisher).publishEvent(any(Object.class));
    }

    private static InterviewSession session() {
        return new InterviewSession(
                SESSION_ID, USER_ID, 10L, 20L, "IN_PROGRESS", "{}", 3, 1, 1, null, null, "key", null, null, null, null);
    }

    private static InterviewQuestion question() {
        return new InterviewQuestion(
                QUESTION_ID,
                SESSION_ID,
                1,
                "DEPTH",
                "MEDIUM",
                "请描述一次线上故障的排查过程？",
                "BANK",
                null,
                List.of(),
                null,
                "{}",
                "{}",
                "ASKED");
    }
}
