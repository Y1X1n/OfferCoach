package com.offercoach.interview.session.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.offercoach.ai.AiRuntimeConfigService;
import com.offercoach.ai.tools.error.ToolErrorKind;
import com.offercoach.ai.tools.error.ToolException;
import com.offercoach.ai.tools.gateway.AiToolGateway;
import com.offercoach.common.error.BizException;
import com.offercoach.common.error.ErrorCode;
import com.offercoach.identity.application.quota.RedisCounterGuard;
import com.offercoach.identity.application.settings.PlatformSettingsService;
import com.offercoach.interview.session.application.port.InterviewQuestionRepository;
import com.offercoach.interview.session.application.port.InterviewSessionRepository;
import com.offercoach.interview.session.domain.InterviewQuestion;
import com.offercoach.interview.session.domain.InterviewSession;
import com.offercoach.interview.session.domain.SessionStatus;
import com.offercoach.interview.support.PlatformSettingsFixtures;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 题目朗读配额语义：能力关闭不计数、上游失败归还、非活动会话拒绝。
 */
class QuestionSpeechServiceTest {

    private static final long USER_ID = 7L;
    private static final long SESSION_ID = 1L;
    private static final long QUESTION_ID = 11L;

    private InterviewSessionRepository sessionRepository;
    private InterviewQuestionRepository questionRepository;
    private AiToolGateway toolGateway;
    private AiRuntimeConfigService runtimeConfig;
    private RedisCounterGuard counterGuard;
    private PlatformSettingsService platformSettings;
    private QuestionSpeechService service;

    @BeforeEach
    void setUp() {
        sessionRepository = mock(InterviewSessionRepository.class);
        questionRepository = mock(InterviewQuestionRepository.class);
        toolGateway = mock(AiToolGateway.class);
        runtimeConfig = mock(AiRuntimeConfigService.class);
        counterGuard = mock(RedisCounterGuard.class);
        platformSettings = mock(PlatformSettingsService.class);

        when(runtimeConfig.tools())
                .thenReturn(new AiRuntimeConfigService.ToolSettings(true, true, true, true, true, "stella"));
        when(platformSettings.snapshot()).thenReturn(PlatformSettingsFixtures.snapshot(3, 5, 20, 20));
        when(sessionRepository.findOwned(USER_ID, SESSION_ID)).thenReturn(Optional.of(session(SessionStatus.READY)));
        when(questionRepository.findOwned(SESSION_ID, QUESTION_ID)).thenReturn(Optional.of(question()));

        service = new QuestionSpeechService(
                sessionRepository, questionRepository, toolGateway, runtimeConfig, counterGuard, platformSettings);
    }

    @Test
    void disabledTtsFailsWithoutCounting() {
        when(runtimeConfig.tools())
                .thenReturn(new AiRuntimeConfigService.ToolSettings(true, false, true, true, true, "stella"));

        assertThatThrownBy(() -> service.speak(USER_ID, SESSION_ID, QUESTION_ID, null))
                .isInstanceOf(BizException.class)
                .satisfies(exception ->
                        assertThat(((BizException) exception).errorCode()).isEqualTo(ErrorCode.AI_UNAVAILABLE));

        verifyNoInteractions(counterGuard);
        verify(toolGateway, never()).speak(anyLong(), anyString(), any(), anyString());
    }

    @Test
    void successfulSpeechCountsPerQuestion() {
        when(toolGateway.speak(anyLong(), anyString(), any(), anyString()))
                .thenReturn(new AiToolGateway.AudioResult(new byte[] {1, 2, 3}, "audio/wav", false));

        QuestionSpeechService.Speech speech = service.speak(USER_ID, SESSION_ID, QUESTION_ID, null);

        assertThat(speech.audio()).containsExactly(1, 2, 3);
        verify(counterGuard).tryAcquireOrThrow(anyString(), eq(3), any(Duration.class), eq("PER_QUESTION"));
        verify(counterGuard, never()).release(anyString());
    }

    @Test
    void upstreamFailureReleasesCountedQuota() {
        when(toolGateway.speak(anyLong(), anyString(), any(), anyString()))
                .thenThrow(new ToolException(ToolErrorKind.UPSTREAM_BUSY));

        assertThatThrownBy(() -> service.speak(USER_ID, SESSION_ID, QUESTION_ID, null))
                .isInstanceOf(BizException.class);

        verify(counterGuard).release(anyString());
    }

    @Test
    void unlimitedConfigDoesNotReleaseOnFailure() {
        when(platformSettings.snapshot()).thenReturn(PlatformSettingsFixtures.snapshot(0, 5, 20, 20));
        when(toolGateway.speak(anyLong(), anyString(), any(), anyString()))
                .thenThrow(new ToolException(ToolErrorKind.UPSTREAM_BUSY));

        assertThatThrownBy(() -> service.speak(USER_ID, SESSION_ID, QUESTION_ID, null))
                .isInstanceOf(BizException.class);

        verify(counterGuard, never()).release(anyString());
        verify(counterGuard, never()).tryAcquireOrThrow(anyString(), anyInt(), any(Duration.class), anyString());
    }

    @Test
    void finishedSessionIsRejectedBeforeCallingUpstream() {
        when(sessionRepository.findOwned(USER_ID, SESSION_ID)).thenReturn(Optional.of(session(SessionStatus.FINISHED)));

        assertThatThrownBy(() -> service.speak(USER_ID, SESSION_ID, QUESTION_ID, null))
                .isInstanceOf(BizException.class)
                .satisfies(exception ->
                        assertThat(((BizException) exception).errorCode()).isEqualTo(ErrorCode.STATE_CONFLICT));

        verify(toolGateway, never()).speak(anyLong(), anyString(), any(), anyString());
    }

    private static InterviewSession session(String status) {
        return new InterviewSession(
                SESSION_ID, USER_ID, 2L, 3L, status, "{}", 5, 0, 1, null, null, "key", null, null, null, null);
    }

    private static InterviewQuestion question() {
        return new InterviewQuestion(
                QUESTION_ID,
                SESSION_ID,
                1,
                "技术深度",
                "中",
                "请介绍一下你最有挑战的项目",
                "BANK",
                null,
                List.of(),
                null,
                null,
                null,
                "READY");
    }
}
