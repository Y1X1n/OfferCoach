package com.offercoach.interview.jd.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offercoach.ai.AiRuntimeConfigService;
import com.offercoach.ai.AiScene;
import com.offercoach.ai.tools.api.ToolDtos;
import com.offercoach.ai.tools.error.ToolException;
import com.offercoach.ai.tools.gateway.AiToolGateway;
import com.offercoach.common.cache.RedisKeys;
import com.offercoach.common.digest.Sha256;
import com.offercoach.common.time.BusinessDate;
import com.offercoach.identity.application.quota.RedisCounterGuard;
import com.offercoach.identity.application.settings.PlatformSettingsService;
import com.offercoach.interview.ai.AiCallLogger;
import com.offercoach.interview.document.WebContentCleaner;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 岗位链接抓取：缓存、每日上限与失败归因。
 *
 * <p>只负责"把 URL 变成清理后的正文或被拒绝的原因"，是否兜底搜索由编排层决定。
 * 能力开关关闭时直接返回 {@code FETCH_DISABLED}（不计数）；上游失败时归还当日计数，
 * 与"仅 200 且有结果计费"的上游口径一致。</p>
 */
@Service
public class JdWebFetchService {

    private static final Logger log = LoggerFactory.getLogger(JdWebFetchService.class);
    private static final Duration CACHE_TTL = Duration.ofHours(1);

    private final AiToolGateway toolGateway;
    private final AiRuntimeConfigService runtimeConfig;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final PlatformSettingsService platformSettings;
    private final RedisCounterGuard counterGuard;
    private final AiCallLogger aiCallLogger;

    public JdWebFetchService(
            AiToolGateway toolGateway,
            AiRuntimeConfigService runtimeConfig,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            PlatformSettingsService platformSettings,
            RedisCounterGuard counterGuard,
            AiCallLogger aiCallLogger) {
        this.toolGateway = toolGateway;
        this.runtimeConfig = runtimeConfig;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.platformSettings = platformSettings;
        this.counterGuard = counterGuard;
        this.aiCallLogger = aiCallLogger;
    }

    /**
     * 抓取并清理正文；命中缓存不回源。失败时返回带原因的 {@link Outcome}，不抛异常。
     */
    public Outcome fetch(long userId, String url) {
        if (!runtimeConfig.tools().fetchEnabled()) {
            return new Outcome(null, null, "FETCH_DISABLED");
        }
        String key = RedisKeys.jdFetchCache(Sha256.hex(url));
        CachedFetch cached = readCache(key);
        if (cached != null) {
            aiCallLogger.log(
                    userId,
                    AiScene.FETCH.name(),
                    "offercoach-cache",
                    "cache",
                    "DEGRADED",
                    null,
                    null,
                    null,
                    0,
                    "CACHE_HIT");
            return new Outcome(cached.cleaned(), cached.title(), cached.reason());
        }
        String counterKey = RedisKeys.dailyFetch(userId, BusinessDate.todayIso());
        counterGuard.tryAcquireOrThrow(
                counterKey, platformSettings.snapshot().fetchDailyLimit(), Duration.ofDays(1), "FETCH");
        try {
            ToolDtos.FetchResponse response = toolGateway.fetch(userId, url);
            String cleaned = WebContentCleaner.clean(response.content() == null ? "" : response.content());
            if (cleaned.isEmpty()) {
                cacheQuietly(key, new CachedFetch(null, null, "FETCH_NO_CONTENT"));
                return new Outcome(null, null, "FETCH_NO_CONTENT");
            }
            cacheQuietly(key, new CachedFetch(cleaned, response.title(), null));
            return new Outcome(cleaned, response.title(), null);
        } catch (ToolException exception) {
            counterGuard.release(counterKey);
            log.info("抓取未成功，转入搜索兜底：kind={}", exception.kind());
            String reason =
                    switch (exception.kind()) {
                        case BLOCKED -> "FETCH_BLOCKED";
                        case NO_CONTENT -> "FETCH_NO_CONTENT";
                        default -> "FETCH_FAILED";
                    };
            cacheQuietly(key, new CachedFetch(null, null, reason));
            return new Outcome(null, null, reason);
        }
    }

    private CachedFetch readCache(String key) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) {
                return null;
            }
            return objectMapper.readValue(json, new TypeReference<CachedFetch>() {});
        } catch (RuntimeException | JsonProcessingException exception) {
            log.warn("抓取缓存读取失败：reason={}", exception.getClass().getSimpleName());
            return null;
        }
    }

    private void cacheQuietly(String key, CachedFetch value) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), CACHE_TTL);
        } catch (RuntimeException | JsonProcessingException exception) {
            log.warn("抓取缓存写入失败：reason={}", exception.getClass().getSimpleName());
        }
    }

    /**
     * 抓取结果：cleaned 非空表示抓到正文，否则 reason 说明失败原因。
     */
    public record Outcome(String cleaned, String title, String reason) {}

    /**
     * 缓存的抓取结果；与 {@link Outcome} 同构，用于命中时保留标题与失败原因。
     */
    public record CachedFetch(String cleaned, String title, String reason) {}
}
