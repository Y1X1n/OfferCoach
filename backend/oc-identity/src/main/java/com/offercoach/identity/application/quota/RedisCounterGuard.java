package com.offercoach.identity.application.quota;

import com.offercoach.common.error.BizException;
import com.offercoach.common.error.ErrorCode;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis 计数守卫：限流/配额类"先增后判"的统一实现。
 *
 * <p>三处共用：岗位搜索/抓取每日上限、单题朗读上限、简历 OCR 每日上限。
 * 语义：`limit <= 0` 视为不限；Redis 不可用时放行（不阻断主流程）；
 * {@link #tryAcquire} 失败不会自动回滚，调用方可在业务失败时 {@link #release} 归还一次。</p>
 */
@Component
public class RedisCounterGuard {

    private static final Logger log = LoggerFactory.getLogger(RedisCounterGuard.class);

    private final StringRedisTemplate redisTemplate;

    public RedisCounterGuard(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 尝试占用一次配额；返回 false 表示已达上限。
     */
    public boolean tryAcquire(String key, int limit, Duration ttl) {
        if (limit <= 0) {
            return true;
        }
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, ttl);
            }
            return count == null || count <= limit;
        } catch (RuntimeException exception) {
            log.warn("配额计数 Redis 不可用，放行：reason={}", exception.getClass().getSimpleName());
            return true;
        }
    }

    /**
     * 占用一次配额，超限时抛 429（带维度标识）。
     */
    public void tryAcquireOrThrow(String key, int limit, Duration ttl, String scope) {
        if (!tryAcquire(key, limit, ttl)) {
            throw new BizException(ErrorCode.RATE_LIMITED, Map.of("limit", limit, "scope", scope));
        }
    }

    /**
     * 归还一次配额（业务失败 / 上游未实际消耗时调用）；计数不低于 0。
     */
    public void release(String key) {
        try {
            Long value = redisTemplate.opsForValue().decrement(key);
            if (value != null && value < 0) {
                redisTemplate.opsForValue().set(key, "0");
            }
        } catch (RuntimeException exception) {
            log.warn("配额归还失败：reason={}", exception.getClass().getSimpleName());
        }
    }
}
