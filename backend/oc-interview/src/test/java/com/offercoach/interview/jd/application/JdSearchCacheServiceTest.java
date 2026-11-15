package com.offercoach.interview.jd.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
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
import com.offercoach.common.error.BizException;
import com.offercoach.common.error.ErrorCode;
import com.offercoach.identity.application.quota.RedisCounterGuard;
import com.offercoach.identity.application.settings.PlatformSettingsService;
import com.offercoach.interview.ai.AiCallLogger;
import com.offercoach.interview.support.JsonCodec;
import com.offercoach.interview.support.PlatformSettingsFixtures;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * 岗位检索的缓存与配额语义：能力关闭不计数、上游失败归还、缓存命中不回源。
 */
class JdSearchCacheServiceTest {

    private static final long USER_ID = 7L;

    private AiToolGateway toolGateway;
    private AiRuntimeConfigService runtimeConfig;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private RedisCounterGuard counterGuard;
    private PlatformSettingsService platformSettings;
    private AiCallLogger aiCallLogger;
    private ObjectMapper objectMapper;
    private JdSearchCacheService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        toolGateway = mock(AiToolGateway.class);
        runtimeConfig = mock(AiRuntimeConfigService.class);
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        counterGuard = mock(RedisCounterGuard.class);
        platformSettings = mock(PlatformSettingsService.class);
        aiCallLogger = mock(AiCallLogger.class);
        objectMapper = new ObjectMapper();
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(runtimeConfig.tools())
                .thenReturn(new AiRuntimeConfigService.ToolSettings(true, true, true, true, true, "stella"));
        when(platformSettings.snapshot()).thenReturn(PlatformSettingsFixtures.snapshot(3, 5, 20, 20));
        service = new JdSearchCacheService(
                toolGateway,
                runtimeConfig,
                redisTemplate,
                objectMapper,
                new JsonCodec(objectMapper),
                platformSettings,
                aiCallLogger,
                counterGuard);
    }

    @Test
    void disabledSearchFailsWithoutCounting() {
        when(runtimeConfig.tools())
                .thenReturn(new AiRuntimeConfigService.ToolSettings(true, true, true, false, true, "stella"));

        assertThatThrownBy(() -> service.search(USER_ID, "后端", 5))
                .isInstanceOf(BizException.class)
                .satisfies(exception ->
                        assertThat(((BizException) exception).errorCode()).isEqualTo(ErrorCode.AI_UNAVAILABLE));

        verify(counterGuard, never()).tryAcquireOrThrow(anyString(), anyInt(), any(Duration.class), anyString());
        verify(toolGateway, never()).search(anyLong(), anyString(), anyInt(), any(), anyBoolean());
    }

    @Test
    void successfulSearchCountsOnce() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(toolGateway.search(anyLong(), anyString(), anyInt(), any(), anyBoolean()))
                .thenReturn(List.of(result("后端工程师", "https://x/1")));

        List<ToolDtos.SearchResult> results = service.search(USER_ID, "后端", 5);

        assertThat(results).hasSize(1);
        verify(counterGuard).tryAcquireOrThrow(anyString(), eq(20), any(Duration.class), eq("SEARCH"));
        verify(counterGuard, never()).release(anyString());
    }

    @Test
    void upstreamFailureReleasesQuota() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(toolGateway.search(anyLong(), anyString(), anyInt(), any(), anyBoolean()))
                .thenThrow(new ToolException(ToolErrorKind.UPSTREAM_BUSY));

        assertThatThrownBy(() -> service.search(USER_ID, "后端", 5)).isInstanceOf(ToolException.class);

        verify(counterGuard).release(anyString());
    }

    @Test
    void cacheHitSkipsUpstreamAndQuota() throws Exception {
        String key = RedisKeys.jdSearchQuery(Sha256.hex("后端|5"));
        when(valueOps.get(eq(key))).thenReturn(objectMapper.writeValueAsString(List.of(result("缓存岗位", "https://x/2"))));

        List<ToolDtos.SearchResult> results = service.search(USER_ID, "后端", 5);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).title()).isEqualTo("缓存岗位");
        verify(toolGateway, never()).search(anyLong(), anyString(), anyInt(), any(), anyBoolean());
        verify(counterGuard, never()).tryAcquireOrThrow(anyString(), anyInt(), any(Duration.class), anyString());
    }

    private static ToolDtos.SearchResult result(String title, String url) {
        return new ToolDtos.SearchResult(1.5, title, url, "snippet", "summary", "content", "站点", null, null, 3);
    }
}
