package com.offercoach.identity.application.quota;

import com.offercoach.common.error.BizException;
import com.offercoach.common.error.ErrorCode;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * Redis 计数守卫：限流/配额类"先增后判"的统一实现。
 *
 * <p>用于防滥用限流（单题朗读上限、岗位搜索/抓取与简历 OCR 每日上限），
 * 与计费额度（{@code quota_ledger} 流水，见 {@code QuotaService}）区分：
 * 前者 Redis 即可，后者以 DB 流水为事实源。语义：`limit <= 0` 视为不限；
 * Redis 不可用时放行（不阻断主流程）；{@link #tryAcquire} 失败不会自动回滚，
 * 调用方可在业务失败时 {@link #release} 归还一次。</p>
 */
@Component
public class RedisCounterGuard {

    private static final Logger log = LoggerFactory.getLogger(RedisCounterGuard.class);

    /** INCR 与首次 TTL 在同一脚本内完成，避免中断或 EXPIRE 失败留下永不过期的计数键。 */
    private static final DefaultRedisScript<Long> INCREMENT_WITH_TTL = new DefaultRedisScript<>(
            """
            local value = redis.call('INCR', KEYS[1])
            if value == 1 then
                redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return value
            """,
            Long.class);

    /** DECR 与负数清理在同一脚本内完成，避免"未经过 tryAcquire 就归还"留下永久 {@code 0} 键。 */
    private static final DefaultRedisScript<Long> DECREMENT = new DefaultRedisScript<>(
            """
            local value = redis.call('DECR', KEYS[1])
            if value < 0 then
                redis.call('DEL', KEYS[1])
                return -1
            end
            return value
            """,
            Long.class);

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
            Long count = redisTemplate.execute(INCREMENT_WITH_TTL, List.of(key), Long.toString(ttl.toMillis()));
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
     * 归还一次配额（业务失败 / 上游未实际消耗时调用）；归零后保留原 TTL。
     */
    public void release(String key) {
        try {
            redisTemplate.execute(DECREMENT, List.of(key));
        } catch (RuntimeException exception) {
            log.warn("配额归还失败：reason={}", exception.getClass().getSimpleName());
        }
    }
}
