package com.offercoach.identity.application;

import com.offercoach.common.cache.RedisKeys;
import com.offercoach.common.error.BizException;
import com.offercoach.common.error.ErrorCode;
import com.offercoach.identity.config.AuthProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Refresh Token 签发、家族轮换与吊销。
 *
 * <p>Redis 仅保存 SHA-256 摘要。Token 键值为 {@code userId:familyId:jti}，同一次登录衍生的
 * Refresh Token 属于同一家族；家族键为 Set，记录家族内全部 Token 摘要。轮换用 GETDEL 原子消费，
 * 被消费的 Token 写入 used 标记；再次出现即判定重放并吊销整个家族。</p>
 */
@Service
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 32;

    private final StringRedisTemplate redisTemplate;
    private final AuthProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(StringRedisTemplate redisTemplate, AuthProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    /**
     * 签发新家族的 Refresh Token（注册/登录）。
     */
    public IssuedRefresh issueNew(long userId) {
        return issue(userId, UUID.randomUUID().toString());
    }

    /**
     * 在既有家族内签发新 Refresh Token（刷新），家族未知时退化为新家族。
     */
    public IssuedRefresh issueInFamily(long userId, String familyId) {
        String family =
                familyId == null || familyId.isBlank() ? UUID.randomUUID().toString() : familyId;
        return issue(userId, family);
    }

    /**
     * 原子消费 Refresh Token；失效或重放抛 REFRESH_INVALID。
     *
     * <p>未命中但存在 used 标记说明该 Token 已被轮换过，按重放处理：吊销整个家族后拒绝。</p>
     */
    public RefreshSession rotate(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BizException(ErrorCode.REFRESH_INVALID);
        }
        String tokenHash = sha256Hex(refreshToken);
        String stored = redisTemplate.opsForValue().getAndDelete(RedisKeys.refreshToken(tokenHash));
        if (stored != null) {
            StoredToken token = parse(stored);
            redisTemplate
                    .opsForValue()
                    .set(RedisKeys.refreshUsed(tokenHash), token.familyId(), properties.getRefreshTokenTtl());
            return new RefreshSession(token.userId(), token.familyId(), token.jti());
        }
        String usedFamily = redisTemplate.opsForValue().get(RedisKeys.refreshUsed(tokenHash));
        if (usedFamily != null && !usedFamily.isBlank()) {
            revokeFamilyByFamilyId(usedFamily);
            redisTemplate.delete(RedisKeys.refreshUsed(tokenHash));
        }
        throw new BizException(ErrorCode.REFRESH_INVALID);
    }

    /**
     * 吊销 Token 所属家族（登出）；Redis 故障直接抛出，登出按 500 失败处理。
     */
    public void revokeFamily(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        String tokenHash = sha256Hex(refreshToken);
        String stored = redisTemplate.opsForValue().get(RedisKeys.refreshToken(tokenHash));
        String familyId = stored != null ? parse(stored).familyId() : null;
        if (familyId == null) {
            familyId = redisTemplate.opsForValue().get(RedisKeys.refreshUsed(tokenHash));
        }
        if (familyId == null || familyId.isBlank()) {
            redisTemplate.delete(RedisKeys.refreshToken(tokenHash));
            return;
        }
        revokeFamilyByFamilyId(familyId);
        redisTemplate.delete(List.of(RedisKeys.refreshToken(tokenHash), RedisKeys.refreshUsed(tokenHash)));
    }

    private IssuedRefresh issue(long userId, String familyId) {
        byte[] random = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(random);
        String refreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        String tokenHash = sha256Hex(refreshToken);
        String jti = UUID.randomUUID().toString();
        String familyKey = RedisKeys.refreshFamily(familyId);
        redisTemplate
                .opsForValue()
                .set(
                        RedisKeys.refreshToken(tokenHash),
                        userId + ":" + familyId + ":" + jti,
                        properties.getRefreshTokenTtl());
        redisTemplate.opsForSet().add(familyKey, tokenHash);
        redisTemplate.expire(familyKey, properties.getRefreshTokenTtl());
        String userFamilies = RedisKeys.refreshUserFamilies(userId);
        redisTemplate.opsForSet().add(userFamilies, familyId);
        redisTemplate.expire(userFamilies, properties.getRefreshTokenTtl());
        return new IssuedRefresh(refreshToken, userId, familyId, jti);
    }

    /**
     * 吊销某用户全部 Refresh 家族；用于停用、改密、角色变更。
     */
    public void revokeAllForUser(long userId) {
        String userFamilies = RedisKeys.refreshUserFamilies(userId);
        Set<String> families = redisTemplate.opsForSet().members(userFamilies);
        if (families != null) {
            families.forEach(this::revokeFamilyByFamilyId);
        }
        redisTemplate.delete(userFamilies);
    }

    private void revokeFamilyByFamilyId(String familyId) {
        String familyKey = RedisKeys.refreshFamily(familyId);
        Set<String> tokenHashes = redisTemplate.opsForSet().members(familyKey);
        if (tokenHashes != null && !tokenHashes.isEmpty()) {
            List<String> tokenKeys =
                    tokenHashes.stream().map(RedisKeys::refreshToken).toList();
            redisTemplate.delete(tokenKeys);
        }
        redisTemplate.delete(familyKey);
    }

    private static StoredToken parse(String stored) {
        int first = stored.indexOf(':');
        int second = first < 0 ? -1 : stored.indexOf(':', first + 1);
        if (first <= 0 || second <= first + 1 || second == stored.length() - 1) {
            throw new BizException(ErrorCode.REFRESH_INVALID);
        }
        long userId;
        try {
            userId = Long.parseLong(stored.substring(0, first));
        } catch (NumberFormatException ex) {
            throw new BizException(ErrorCode.REFRESH_INVALID);
        }
        String familyId = stored.substring(first + 1, second);
        String jti = stored.substring(second + 1);
        if (jti.indexOf(':') >= 0) {
            throw new BizException(ErrorCode.REFRESH_INVALID);
        }
        return new StoredToken(userId, familyId, jti);
    }

    private static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }

    private record StoredToken(long userId, String familyId, String jti) {}

    /**
     * 新签发的 Refresh Token 及其家族信息。
     */
    public record IssuedRefresh(String token, long userId, String familyId, String jti) {}

    /**
     * 轮换消费结果；调用方需在同一家族内续签。
     */
    public record RefreshSession(long userId, String familyId, String oldJti) {}
}
