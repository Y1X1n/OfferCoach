package com.offercoach.interview.jd.application;

import com.offercoach.common.cache.RedisKeys;
import com.offercoach.common.error.BizException;
import com.offercoach.common.error.ErrorCode;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 联网获取的每日次数守卫：超限抛 429；Redis 不可用时放行（不阻断主流程）。
 */
@Component
public class JdDailyLimitGuard {

    private static final Logger log = LoggerFactory.getLogger(JdDailyLimitGuard.class);

    private final StringRedisTemplate redisTemplate;

    public JdDailyLimitGuard(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * @param scope 用于错误详情的维度标识（SEARCH / FETCH）
     */
    public void enforce(long userId, String key, int limit, String scope) {
        if (limit <= 0) {
            return;
        }
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, Duration.ofDays(1));
            }
            if (count != null && count > limit) {
                throw new BizException(ErrorCode.RATE_LIMITED, Map.of("limit", limit, "scope", scope));
            }
        } catch (BizException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn(
                    "限额 Redis 不可用，放行：scope={} reason={}",
                    scope,
                    exception.getClass().getSimpleName());
        }
    }

    public static String searchKey(long userId) {
        return RedisKeys.dailySearch(userId, LocalDate.now().toString());
    }

    public static String fetchKey(long userId) {
        return RedisKeys.dailyFetch(userId, LocalDate.now().toString());
    }
}
