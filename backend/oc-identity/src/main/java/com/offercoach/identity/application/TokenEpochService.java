package com.offercoach.identity.application;

import com.offercoach.common.cache.RedisKeys;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Access Token 版本号：停用、降权、重置密码时递增，使已签发的 Access Token 立即失效。
 */
@Service
public class TokenEpochService {

    private final StringRedisTemplate redisTemplate;

    public TokenEpochService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public long current(long userId) {
        String value = redisTemplate.opsForValue().get(RedisKeys.accessTokenEpoch(userId));
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    public void bump(long userId) {
        redisTemplate.opsForValue().increment(RedisKeys.accessTokenEpoch(userId));
    }
}
