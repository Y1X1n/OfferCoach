package com.offercoach.interview.jd.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offercoach.ai.AiRuntimeConfigService;
import com.offercoach.ai.tools.api.ToolDtos;
import com.offercoach.ai.tools.error.ToolErrorKind;
import com.offercoach.ai.tools.error.ToolException;
import com.offercoach.ai.tools.gateway.AiToolGateway;
import com.offercoach.common.cache.RedisKeys;
import com.offercoach.common.digest.Sha256;
import com.offercoach.identity.application.quota.RedisCounterGuard;
import com.offercoach.identity.application.settings.PlatformSettingsService;
import com.offercoach.interview.ai.AiCallLogger;
import com.offercoach.interview.support.PlatformSettingsFixtures;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * 岗位抓取的缓存与配额语义：能力关闭不计数、失败归还并保留失败原因、命中保留标题。
 */
class JdWebFetchServiceTest {

    private static final long USER_ID = 7L;
    private static final String URL = "https://jobs.example.com/backend/1";

    private AiToolGateway toolGateway;
    private AiRuntimeConfigService runtimeConfig;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private RedisCounterGuard counterGuard;
    private PlatformSettingsService platformSettings;
    private ObjectMapper objectMapper;
    private JdWebFetchService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        toolGateway = mock(AiToolGateway.class);
        runtimeConfig = mock(AiRuntimeConfigService.class);
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        counterGuard = mock(RedisCounterGuard.class);
        platformSettings = mock(PlatformSettingsService.class);
        objectMapper = new ObjectMapper();
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(runtimeConfig.tools())
                .thenReturn(new AiRuntimeConfigService.ToolSettings(true, true, true, true, true, "stella"));
        when(platformSettings.snapshot()).thenReturn(PlatformSettingsFixtures.snapshot(3, 5, 20, 20));
        service = new JdWebFetchService(
                toolGateway,
                runtimeConfig,
                redisTemplate,
                objectMapper,
                platformSettings,
                counterGuard,
                mock(AiCallLogger.class));
    }

    @Test
    void disabledFetchReturnsDisabledWithoutCounting() {
        when(runtimeConfig.tools())
                .thenReturn(new AiRuntimeConfigService.ToolSettings(true, true, true, true, false, "stella"));

        JdWebFetchService.Outcome outcome = service.fetch(USER_ID, URL);

        assertThat(outcome.reason()).isEqualTo("FETCH_DISABLED");
        assertThat(outcome.cleaned()).isNull();
        verify(counterGuard, never()).tryAcquireOrThrow(anyString(), anyInt(), any(Duration.class), anyString());
        verify(toolGateway, never()).fetch(anyLong(), anyString());
    }

    @Test
    void successfulFetchCountsAndReturnsTitle() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(toolGateway.fetch(anyLong(), anyString()))
                .thenReturn(new ToolDtos.FetchResponse("高级后端工程师", "# 岗位职责\n负责订单履约系统的后端开发，要求三年以上 Java 经验。"));

        JdWebFetchService.Outcome outcome = service.fetch(USER_ID, URL);

        assertThat(outcome.title()).isEqualTo("高级后端工程师");
        assertThat(outcome.cleaned()).contains("后端开发");
        assertThat(outcome.reason()).isNull();
        verify(counterGuard).tryAcquireOrThrow(anyString(), eq(20), any(Duration.class), eq("FETCH"));
    }

    @Test
    void upstreamFailureReleasesQuotaAndKeepsReason() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(toolGateway.fetch(anyLong(), anyString())).thenThrow(new ToolException(ToolErrorKind.BLOCKED));

        JdWebFetchService.Outcome outcome = service.fetch(USER_ID, URL);

        assertThat(outcome.reason()).isEqualTo("FETCH_BLOCKED");
        verify(counterGuard).release(anyString());
    }

    @Test
    void blankBodyYieldsNoContent() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(toolGateway.fetch(anyLong(), anyString())).thenReturn(new ToolDtos.FetchResponse("标题", "   "));

        assertThat(service.fetch(USER_ID, URL).reason()).isEqualTo("FETCH_NO_CONTENT");
    }

    @Test
    void cacheHitPreservesTitleAndReasonAndSkipsQuota() throws Exception {
        String key = RedisKeys.jdFetchCache(Sha256.hex(URL));
        JdWebFetchService.CachedFetch cached = new JdWebFetchService.CachedFetch("清理后的正文", "缓存标题", null);
        when(valueOps.get(eq(key))).thenReturn(objectMapper.writeValueAsString(cached));

        JdWebFetchService.Outcome outcome = service.fetch(USER_ID, URL);

        assertThat(outcome.cleaned()).isEqualTo("清理后的正文");
        assertThat(outcome.title()).isEqualTo("缓存标题");
        verify(toolGateway, never()).fetch(anyLong(), anyString());
        verify(counterGuard, never()).tryAcquireOrThrow(anyString(), anyInt(), any(Duration.class), anyString());
    }

    @Test
    void cachedFailureKeepsSpecificReasonNotCollapsed() throws Exception {
        String key = RedisKeys.jdFetchCache(Sha256.hex(URL));
        JdWebFetchService.CachedFetch cached = new JdWebFetchService.CachedFetch(null, null, "FETCH_NO_CONTENT");
        when(valueOps.get(eq(key))).thenReturn(objectMapper.writeValueAsString(cached));

        assertThat(service.fetch(USER_ID, URL).reason()).isEqualTo("FETCH_NO_CONTENT");
    }
}
