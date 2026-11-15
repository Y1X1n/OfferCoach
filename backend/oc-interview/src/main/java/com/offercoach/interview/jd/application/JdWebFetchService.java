package com.offercoach.interview.jd.application;

import com.offercoach.ai.tools.api.ToolDtos;
import com.offercoach.ai.tools.error.ToolException;
import com.offercoach.ai.tools.gateway.AiToolGateway;
import com.offercoach.common.cache.RedisKeys;
import com.offercoach.identity.application.quota.RedisCounterGuard;
import com.offercoach.identity.application.settings.PlatformSettingsService;
import com.offercoach.interview.ai.AiCallLogger;
import com.offercoach.interview.document.WebContentCleaner;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 岗位链接抓取：缓存、每日上限与失败归因。
 *
 * <p>只负责"把 URL 变成清理后的正文或被拒绝的原因"，是否兜底搜索由编排层决定。</p>
 */
@Service
public class JdWebFetchService {

    private static final Logger log = LoggerFactory.getLogger(JdWebFetchService.class);
    private static final Duration CACHE_TTL = Duration.ofHours(1);

    private final AiToolGateway toolGateway;
    private final StringRedisTemplate redisTemplate;
    private final PlatformSettingsService platformSettings;
    private final RedisCounterGuard counterGuard;
    private final AiCallLogger aiCallLogger;

    public JdWebFetchService(
            AiToolGateway toolGateway,
            StringRedisTemplate redisTemplate,
            PlatformSettingsService platformSettings,
            RedisCounterGuard counterGuard,
            AiCallLogger aiCallLogger) {
        this.toolGateway = toolGateway;
        this.redisTemplate = redisTemplate;
        this.platformSettings = platformSettings;
        this.counterGuard = counterGuard;
        this.aiCallLogger = aiCallLogger;
    }

    /**
     * 抓取并清理正文；命中缓存不回源。失败时返回带原因的 {@link Outcome}，不抛异常。
     */
    public Outcome fetch(long userId, String url) {
        String key = RedisKeys.jdFetchCache(digest(url));
        try {
            String cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                aiCallLogger.log(
                        userId, "FETCH", "offercoach-cache", "cache", "DEGRADED", null, null, null, 0, "CACHE_HIT");
                return cached.isEmpty() ? new Outcome(null, null, "FETCH_BLOCKED") : new Outcome(cached, null, null);
            }
        } catch (RuntimeException exception) {
            log.warn("抓取缓存读取失败：reason={}", exception.getClass().getSimpleName());
        }
        counterGuard.tryAcquireOrThrow(
                RedisKeys.dailyFetch(userId, LocalDate.now().toString()),
                platformSettings.snapshot().fetchDailyLimit(),
                Duration.ofDays(1),
                "FETCH");
        try {
            ToolDtos.FetchResponse response = toolGateway.fetch(userId, url);
            String cleaned = WebContentCleaner.clean(response.content() == null ? "" : response.content());
            cacheQuietly(key, cleaned);
            return new Outcome(cleaned, response.title(), null);
        } catch (ToolException exception) {
            log.info("抓取未成功，转入搜索兜底：kind={}", exception.kind());
            String reason =
                    switch (exception.kind()) {
                        case BLOCKED -> "FETCH_BLOCKED";
                        case NO_CONTENT -> "FETCH_NO_CONTENT";
                        default -> "FETCH_FAILED";
                    };
            cacheQuietly(key, "");
            return new Outcome(null, null, reason);
        }
    }

    private void cacheQuietly(String key, String value) {
        try {
            redisTemplate.opsForValue().set(key, value, CACHE_TTL);
        } catch (RuntimeException exception) {
            log.warn("抓取缓存写入失败：reason={}", exception.getClass().getSimpleName());
        }
    }

    static String digest(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    /**
     * 抓取结果：cleaned 非空表示抓到正文，否则 reason 说明失败原因。
     */
    public record Outcome(String cleaned, String title, String reason) {}
}
