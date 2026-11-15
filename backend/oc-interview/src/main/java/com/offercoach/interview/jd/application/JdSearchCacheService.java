package com.offercoach.interview.jd.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offercoach.ai.AiRuntimeConfigService;
import com.offercoach.ai.tools.api.ToolDtos;
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
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 岗位检索与候选缓存：查询级缓存、每日限额、候选正文短期缓存（供预览复用，避免重复计费）。
 *
 * <p>能力开关关闭时按不可用处理（不计数）；上游失败时归还当日计数。</p>
 */
@Service
public class JdSearchCacheService {

    private static final Logger log = LoggerFactory.getLogger(JdSearchCacheService.class);
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    private final AiToolGateway toolGateway;
    private final AiRuntimeConfigService runtimeConfig;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final JsonCodec jsonCodec;
    private final PlatformSettingsService platformSettings;
    private final AiCallLogger aiCallLogger;
    private final RedisCounterGuard counterGuard;

    public JdSearchCacheService(
            AiToolGateway toolGateway,
            AiRuntimeConfigService runtimeConfig,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            JsonCodec jsonCodec,
            PlatformSettingsService platformSettings,
            AiCallLogger aiCallLogger,
            RedisCounterGuard counterGuard) {
        this.toolGateway = toolGateway;
        this.runtimeConfig = runtimeConfig;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.jsonCodec = jsonCodec;
        this.platformSettings = platformSettings;
        this.aiCallLogger = aiCallLogger;
        this.counterGuard = counterGuard;
    }

    /**
     * 执行检索：命中查询缓存不回源；未命中时校验每日限额后调用上游。
     */
    public List<ToolDtos.SearchResult> search(long userId, String query, int count) {
        if (!runtimeConfig.tools().searchEnabled()) {
            throw new BizException(
                    ErrorCode.AI_UNAVAILABLE,
                    Map.of("reason", "NOT_CONFIGURED", "message", "联网搜索已被管理员关闭"));
        }
        String cacheKey = RedisKeys.jdSearchQuery(Sha256.hex(query + "|" + count));
        long startedAt = System.nanoTime();
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                aiCallLogger.log(
                        userId,
                        "SEARCH",
                        "offercoach-cache",
                        "cache",
                        "DEGRADED",
                        null,
                        null,
                        null,
                        (System.nanoTime() - startedAt) / 1_000_000,
                        "CACHE_HIT");
                return readValue(cached, new TypeReference<List<ToolDtos.SearchResult>>() {});
            }
        } catch (RuntimeException exception) {
            log.warn("检索缓存读取失败：reason={}", exception.getClass().getSimpleName());
        }
        String counterKey = RedisKeys.dailySearch(userId, LocalDate.now().toString());
        counterGuard.tryAcquireOrThrow(
                counterKey, platformSettings.snapshot().searchDailyLimit(), Duration.ofDays(1), "SEARCH");
        List<ToolDtos.SearchResult> results;
        try {
            results = toolGateway.search(userId, query, count, null, true);
        } catch (ToolException exception) {
            counterGuard.release(counterKey);
            throw exception;
        }
        cacheQuietly(cacheKey, jsonCodec.write(results));
        return results;
    }

    /**
     * 把本次候选结果缓存到短期 token 下，供预览/导入复用。
     */
    public String publish(List<ToolDtos.SearchResult> results, String fallbackReason) {
        String token = "st_" + UUID.randomUUID().toString().replace("-", "");
        cacheQuietly(RedisKeys.jdSearchCache(token), jsonCodec.write(new SearchCacheEntry(fallbackReason, results)));
        return token;
    }

    /**
     * 读取候选缓存；缺失或格式不兼容时按"已过期"处理。
     */
    public SearchCacheEntry read(String token) {
        String json;
        try {
            json = redisTemplate.opsForValue().get(RedisKeys.jdSearchCache(token));
        } catch (RuntimeException exception) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, Map.of("reason", "CACHE_UNAVAILABLE"));
        }
        if (json == null) {
            throw expired();
        }
        try {
            return readValue(json, new TypeReference<SearchCacheEntry>() {});
        } catch (BizException exception) {
            throw expired();
        }
    }

    private void cacheQuietly(String key, String value) {
        try {
            redisTemplate.opsForValue().set(key, value, CACHE_TTL);
        } catch (RuntimeException exception) {
            log.warn("候选缓存写入失败，预览将回源：reason={}", exception.getClass().getSimpleName());
        }
    }

    private <T> T readValue(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, Map.of("reason", "CACHE_CORRUPTED"));
        }
    }

    private static BizException expired() {
        return new BizException(ErrorCode.STATE_CONFLICT, Map.of("reason", "SEARCH_EXPIRED", "message", "候选已过期，请重新搜索"));
    }

    /**
     * 候选缓存条目：记录本次结果是否来自"链接抓取失败后的搜索兜底"，供后续预览标注来源。
     */
    public record SearchCacheEntry(String fallbackReason, List<ToolDtos.SearchResult> results) {}
}
