package com.offercoach.identity.security;

import com.offercoach.common.cache.RedisKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * 校验 Access Token 是否已被登出拉黑。
 *
 * <p>Redis 异常时 fail-closed：拒绝该 Token 并按服务不可用返回，避免已登出 Token 在缓存故障期间继续可用。</p>
 */
@Component
public class TokenBlacklistValidator implements OAuth2TokenValidator<Jwt> {

    private static final Logger log = LoggerFactory.getLogger(TokenBlacklistValidator.class);
    private static final OAuth2Error REVOKED = new OAuth2Error("invalid_token", "Token has been revoked", null);
    private static final OAuth2Error UNAVAILABLE = new OAuth2Error("server_error", "Token status unavailable", null);

    private final StringRedisTemplate redisTemplate;

    public TokenBlacklistValidator(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        String jwtId = token.getId();
        if (jwtId == null || jwtId.isBlank()) {
            return OAuth2TokenValidatorResult.success();
        }
        try {
            Boolean blacklisted = redisTemplate.hasKey(RedisKeys.accessBlacklist(jwtId));
            if (Boolean.TRUE.equals(blacklisted)) {
                return OAuth2TokenValidatorResult.failure(REVOKED);
            }
        } catch (RuntimeException ex) {
            log.warn("Access Token 黑名单校验失败，按拒绝处理：{}", ex.getClass().getSimpleName());
            return OAuth2TokenValidatorResult.failure(UNAVAILABLE);
        }
        return OAuth2TokenValidatorResult.success();
    }
}
