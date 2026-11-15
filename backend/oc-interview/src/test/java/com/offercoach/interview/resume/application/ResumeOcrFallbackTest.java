package com.offercoach.interview.resume.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.offercoach.ai.AiRuntimeConfigService;
import com.offercoach.ai.tools.api.ToolDtos;
import com.offercoach.ai.tools.error.ToolErrorKind;
import com.offercoach.ai.tools.error.ToolException;
import com.offercoach.ai.tools.gateway.AiToolGateway;
import com.offercoach.identity.application.quota.RedisCounterGuard;
import com.offercoach.identity.application.settings.PlatformSettingsService;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * OCR 降级：能力开关、每日上限与失败归还。
 */
class ResumeOcrFallbackTest {

    private static final long USER_ID = 73L;
    private static final byte[] CONTENT = new byte[] {1, 2, 3};

    private AiToolGateway gateway;
    private AiRuntimeConfigService runtimeConfig;
    private PlatformSettingsService platformSettings;
    private PlatformSettingsService.Snapshot snapshot;
    private RedisCounterGuard counterGuard;
    private ResumeOcrFallback fallback;

    @BeforeEach
    void setUp() {
        gateway = mock(AiToolGateway.class);
        runtimeConfig = mock(AiRuntimeConfigService.class);
        platformSettings = mock(PlatformSettingsService.class);
        snapshot = mock(PlatformSettingsService.Snapshot.class);
        counterGuard = mock(RedisCounterGuard.class);
        when(runtimeConfig.tools())
                .thenReturn(new AiRuntimeConfigService.ToolSettings(true, true, true, true, true, "stella"));
        when(platformSettings.snapshot()).thenReturn(snapshot);
        fallback = new ResumeOcrFallback(gateway, runtimeConfig, platformSettings, counterGuard);
    }

    @Test
    void skipsWhenCapabilityDisabled() {
        when(runtimeConfig.tools())
                .thenReturn(new AiRuntimeConfigService.ToolSettings(true, true, false, true, true, "stella"));

        assertThat(fallback.extract(USER_ID, CONTENT, "a.png", "image/png")).isNull();

        verifyNoInteractions(gateway, counterGuard);
    }

    @Test
    void skipsWhenDailyLimitReached() {
        when(snapshot.ocrDailyLimit()).thenReturn(5);
        when(counterGuard.tryAcquire(any(), eq(5), any(Duration.class))).thenReturn(false);

        assertThat(fallback.extract(USER_ID, CONTENT, "a.png", "image/png")).isNull();

        verifyNoInteractions(gateway);
    }

    @Test
    void doesNotConsumeQuotaWhenUnlimitedAndUpstreamFails() {
        when(snapshot.ocrDailyLimit()).thenReturn(0);
        when(gateway.recognize(anyLong(), any(), any(), any(), any()))
                .thenThrow(new ToolException(ToolErrorKind.UPSTREAM_BUSY));

        assertThat(fallback.extract(USER_ID, CONTENT, "a.png", "image/png")).isNull();

        verifyNoInteractions(counterGuard);
    }

    @Test
    void returnsTextAndKeepsQuotaOnSuccess() {
        when(snapshot.ocrDailyLimit()).thenReturn(5);
        when(counterGuard.tryAcquire(any(), eq(5), any(Duration.class))).thenReturn(true);
        when(gateway.recognize(anyLong(), any(), any(), any(), any()))
                .thenReturn(
                        new ToolDtos.OcrResult("ocr-1", 1, List.of(new ToolDtos.OcrPage(0, "岗位职责 1. 负责订单履约", "stop"))));

        String text = fallback.extract(USER_ID, CONTENT, "a.png", "image/png");

        assertThat(text).contains("岗位职责").contains("订单履约");
        verify(counterGuard, never()).release(any());
    }

    @Test
    void refundsQuotaWhenUpstreamFails() {
        when(snapshot.ocrDailyLimit()).thenReturn(5);
        when(counterGuard.tryAcquire(any(), eq(5), any(Duration.class))).thenReturn(true);
        when(gateway.recognize(anyLong(), any(), any(), any(), any()))
                .thenThrow(new ToolException(ToolErrorKind.UPSTREAM_FAILED));

        assertThat(fallback.extract(USER_ID, CONTENT, "a.png", "image/png")).isNull();

        verify(counterGuard).release(any());
    }

    @Test
    void refundsQuotaWhenResultIsEmpty() {
        when(snapshot.ocrDailyLimit()).thenReturn(5);
        when(counterGuard.tryAcquire(any(), eq(5), any(Duration.class))).thenReturn(true);
        when(gateway.recognize(anyLong(), any(), any(), any(), any()))
                .thenReturn(new ToolDtos.OcrResult("ocr-2", 1, List.of(new ToolDtos.OcrPage(0, "   ", "stop"))));

        assertThat(fallback.extract(USER_ID, CONTENT, "a.png", "image/png")).isNull();

        verify(counterGuard).release(any());
    }

    @Test
    void requiresOcrOnlyForImagesAndSlides() {
        assertThat(fallback.requiresOcr("png")).isTrue();
        assertThat(fallback.requiresOcr("pptx")).isTrue();
        assertThat(fallback.requiresOcr("pdf")).isFalse();
        assertThat(fallback.requiresOcr(null)).isFalse();
    }
}
