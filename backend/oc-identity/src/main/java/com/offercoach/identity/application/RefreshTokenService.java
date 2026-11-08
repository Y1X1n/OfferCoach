package com.offercoach.identity.application;

import com.offercoach.common.cache.RedisKeys;
import com.offercoach.common.error.BizException;
import com.offercoach.common.error.ErrorCode;
import com.offercoach.identity.config.AuthProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/**
 * Refresh Token 签发、家族轮换与吊销。
 *
 * <p>Redis 仅保存 SHA-256 摘要。Token 键值为 {@code userId:familyId:jti}，同一次登录衍生的
 * Refresh Token 属于同一家族；家族键为 Set，记录家族内全部 Token 摘要。轮换用脚本原子消费并写入
 * used 标记；再次出现时在宽限期内视为并发刷新并同族补发，超过宽限期判定重放并吊销整个家族。</p>
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final int TOKEN_BYTES = 32;
    private static final String USED_SEPARATOR = "|";
    private static final DefaultRedisScript<String> CONSUME_SCRIPT = new DefaultRedisScript<>(
            """
            local value = redis.call('GET', KEYS[1])
            if not value then
                return nil
            end
            redis.call('DEL', KEYS[1])
            redis.call('SET', KEYS[2], value .. '|' .. ARGV[1], 'PX', ARGV[2])
            return value
            """,
            String.class);

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
     *
     * <p>家族墓碑存在时拒绝签发；签发后复查墓碑，覆盖“检查通过后恰逢登出”的竞态。</p>
     */
    public IssuedRefresh issueInFamily(long userId, String familyId) {
        String family =
                familyId == null || familyId.isBlank() ? UUID.randomUUID().toString() : familyId;
        if (isRevoked(family)) {
            throw new BizException(ErrorCode.REFRESH_INVALID);
        }
        IssuedRefresh issued = issue(userId, family);
        if (isRevoked(family)) {
            revokeFamilyByFamilyId(family);
            throw new BizException(ErrorCode.REFRESH_INVALID);
        }
        return issued;
    }

    private boolean isRevoked(String familyId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(RedisKeys.refreshFamilyRevoked(familyId)));
    }

    /**
     * 原子消费 Refresh Token；失效或超期重放抛 REFRESH_INVALID。
     *
     * <p>未命中但存在 used 标记时：宽限期内且家族仍存活，说明是并发/多标签页刷新，
     * 在原家族内补发；超过宽限期或家族已吊销则按重放处理，吊销整个家族。</p>
     */
    public RefreshSession rotate(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BizException(ErrorCode.REFRESH_INVALID);
        }
        String tokenHash = sha256Hex(refreshToken);
        String stored = redisTemplate.execute(
                CONSUME_SCRIPT,
                List.of(RedisKeys.refreshToken(tokenHash), RedisKeys.refreshUsed(tokenHash)),
                Long.toString(System.currentTimeMillis()),
                Long.toString(properties.getRefreshTokenTtl().toMillis()));
        if (stored != null) {
            StoredToken token = parse(stored);
            return new RefreshSession(token.userId(), token.familyId(), token.jti());
        }
        String used = redisTemplate.opsForValue().get(RedisKeys.refreshUsed(tokenHash));
        if (used != null && !used.isBlank()) {
            UsedToken usedToken = parseUsed(used);
            if (usedToken != null && withinGrace(usedToken.rotatedAt()) && familyAlive(usedToken.familyId())) {
                log.info("刷新 Token 宽限期内重复提交，同族补发：familyId={}", usedToken.familyId());
                return new RefreshSession(usedToken.userId(), usedToken.familyId(), usedToken.jti());
            }
            revokeFamilyByFamilyId(usedToken != null ? usedToken.familyId() : used);
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
            String used = redisTemplate.opsForValue().get(RedisKeys.refreshUsed(tokenHash));
            if (used != null && !used.isBlank()) {
                UsedToken usedToken = parseUsed(used);
                familyId = usedToken != null ? usedToken.familyId() : used;
            }
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
        redisTemplate
                .opsForValue()
                .set(
                        RedisKeys.refreshFamilyRevoked(familyId),
                        "1",
                        properties.getRefreshTokenTtl().plus(Duration.ofHours(1)));
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

    /**
     * 解析 used 标记：新格式为 {@code userId:familyId:jti|rotatedAtMillis}，旧格式仅家族 ID。
     */
    private static UsedToken parseUsed(String value) {
        int separator = value.lastIndexOf(USED_SEPARATOR);
        if (separator <= 0 || separator == value.length() - 1) {
            return null;
        }
        try {
            StoredToken token = parse(value.substring(0, separator));
            long rotatedAt = Long.parseLong(value.substring(separator + 1));
            return new UsedToken(token.userId(), token.familyId(), token.jti(), rotatedAt);
        } catch (BizException | NumberFormatException exception) {
            return null;
        }
    }

    private boolean withinGrace(long rotatedAt) {
        long graceMillis = properties.getRefreshGrace().toMillis();
        if (graceMillis <= 0) {
            return false;
        }
        long elapsed = System.currentTimeMillis() - rotatedAt;
        return elapsed >= 0 && elapsed <= graceMillis;
    }

    /**
     * 家族键存在才允许宽限补发：登出/改密/停用会删除家族键，避免宽限期绕过吊销。
     */
    private boolean familyAlive(String familyId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(RedisKeys.refreshFamily(familyId)));
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

    private record UsedToken(long userId, String familyId, String jti, long rotatedAt) {}

    /**
     * 新签发的 Refresh Token 及其家族信息。
     */
    public record IssuedRefresh(String token, long userId, String familyId, String jti) {}

    /**
     * 轮换消费结果；调用方需在同一家族内续签。
     */
    public record RefreshSession(long userId, String familyId, String oldJti) {}
}
