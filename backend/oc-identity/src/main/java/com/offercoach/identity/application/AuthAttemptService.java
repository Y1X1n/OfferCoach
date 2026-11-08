package com.offercoach.identity.application;

import com.offercoach.common.cache.RedisKeys;
import com.offercoach.common.error.BizException;
import com.offercoach.common.error.ErrorCode;
import com.offercoach.identity.application.settings.PlatformSettingsService;
import com.offercoach.identity.config.RateLimitProperties;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 登录/注册限流与账号失败锁定。
 *
 * <p>窗口计数全部落在 Redis：IP 维度按分钟固定窗口计数，账号维度统计近期凭证失败次数。Redis
 * 不可用时告警并放行，避免缓存故障导致无法登录；计数键设计保留，恢复后自动生效。</p>
 */
@Service
public class AuthAttemptService {

    private static final Logger log = LoggerFactory.getLogger(AuthAttemptService.class);
    private static final Duration WINDOW = Duration.ofSeconds(60);
    private static final int MAX_ACCOUNT_KEY_LENGTH = 320;
    private static final DateTimeFormatter DAY_KEY = DateTimeFormatter.BASIC_ISO_DATE;

    private final StringRedisTemplate redisTemplate;
    private final RateLimitProperties properties;
    private final PlatformSettingsService settingsService;

    public AuthAttemptService(
            StringRedisTemplate redisTemplate,
            RateLimitProperties properties,
            PlatformSettingsService settingsService) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.settingsService = settingsService;
    }

    /**
     * 注册限流：单 IP 每分钟最多 {@code register-per-minute} 次尝试。
     */
    public void checkRegisterAllowed(String ip) {
        enforceWindow(RedisKeys.registerRateLimit(ip), properties.getRegisterPerMinute(), "注册");
    }

    /**
     * 注册日限：单 IP / 单设备每日成功注册数达到阈值时拒绝；Redis 故障放行。
     */
    public void checkRegistrationDailyCaps(String ip, String deviceHash) {
        PlatformSettingsService.Snapshot settings = settingsService.snapshot();
        String date = LocalDate.now().format(DAY_KEY);
        if (settings.registrationIpDailyCap() > 0) {
            Long used = readCounter(RedisKeys.registerIpDaily(ip, date));
            if (used != null && used >= settings.registrationIpDailyCap()) {
                log.warn("注册 IP 日限触发：ip={} cap={}", ip, settings.registrationIpDailyCap());
                throw new BizException(
                        ErrorCode.RATE_LIMITED,
                        Map.of(
                                "scope",
                                "REGISTER_IP_DAILY",
                                "limit",
                                settings.registrationIpDailyCap(),
                                "window",
                                "1d"));
            }
        }
        if (deviceHash != null && !deviceHash.isBlank() && settings.registrationDeviceDailyCap() > 0) {
            Long used = readCounter(RedisKeys.registerDeviceDaily(deviceHash, date));
            if (used != null && used >= settings.registrationDeviceDailyCap()) {
                log.warn("注册设备日限触发：cap={}", settings.registrationDeviceDailyCap());
                throw new BizException(
                        ErrorCode.RATE_LIMITED,
                        Map.of(
                                "scope",
                                "REGISTER_DEVICE_DAILY",
                                "limit",
                                settings.registrationDeviceDailyCap(),
                                "window",
                                "1d"));
            }
        }
    }

    /**
     * 注册成功后累加单 IP / 单设备日计数；仅用于风控，失败不阻断注册。
     */
    public void onRegistrationSuccess(String ip, String deviceHash) {
        String date = LocalDate.now().format(DAY_KEY);
        incrementDaily(RedisKeys.registerIpDaily(ip, date));
        if (deviceHash != null && !deviceHash.isBlank()) {
            incrementDaily(RedisKeys.registerDeviceDaily(deviceHash, date));
        }
    }

    private void incrementDaily(String key) {
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, Duration.ofDays(2));
            }
        } catch (RuntimeException ex) {
            log.warn("注册日计数写入失败，本次不累计：{}", ex.getClass().getSimpleName());
        }
    }

    /**
     * 登录尝试预占：单 IP 分钟窗口 + 账号尝试计数原子递增后再判定。
     *
     * <p>先占用再校验密码，避免并发请求都读到阈值以下的计数而放大校验次数；
     * 登录成功由 {@link #onLoginSuccess(LoginAttempt, String)} 清零。</p>
     */
    public LoginAttempt reserveLoginAttempt(String account, String ip) {
        enforceWindow(RedisKeys.loginRateLimit(ip), properties.getLoginPerMinute(), "登录");
        String key = accountKey(account);
        if (key == null) {
            return LoginAttempt.untracked();
        }
        try {
            Long attempts = redisTemplate.opsForValue().increment(key);
            if (attempts != null && attempts == 1L) {
                redisTemplate.expire(key, properties.getFailWindow());
            }
            if (attempts != null && attempts > properties.getFailThreshold()) {
                log.warn("账号登录失败次数达到阈值，暂时锁定：{}", mask(account));
                throw new BizException(ErrorCode.RATE_LIMITED);
            }
            return new LoginAttempt(key, true);
        } catch (BizException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn("登录尝试计数写入失败，本次不累计：{}", exception.getClass().getSimpleName());
            return LoginAttempt.untracked();
        }
    }

    /**
     * 凭证错误：预占成功时计数已生效，不再重复累加；Redis 异常导致未计数时补一次。
     */
    public void onLoginFailure(LoginAttempt attempt, String account, String ip) {
        log.warn("登录凭证校验失败 ip={} account={}", ip == null || ip.isBlank() ? "-" : ip, mask(account));
        if (attempt != null && attempt.counted()) {
            return;
        }
        String key = attempt != null && attempt.key() != null ? attempt.key() : accountKey(account);
        if (key == null) {
            return;
        }
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, properties.getFailWindow());
            }
        } catch (RuntimeException ex) {
            log.warn("登录失败计数写入失败，本次不累计：{}", ex.getClass().getSimpleName());
        }
    }

    /**
     * 登录成功清除账号失败计数（含本次预占）。
     */
    public void onLoginSuccess(LoginAttempt attempt, String account) {
        String key = attempt != null && attempt.key() != null ? attempt.key() : accountKey(account);
        if (key == null) {
            return;
        }
        try {
            redisTemplate.delete(key);
        } catch (RuntimeException ex) {
            log.warn("登录失败计数清理失败：{}", ex.getClass().getSimpleName());
        }
    }

    /**
     * 登录尝试预占结果；账号为空或 Redis 故障时为未计数。
     */
    public record LoginAttempt(String key, boolean counted) {

        static LoginAttempt untracked() {
            return new LoginAttempt(null, false);
        }
    }

    private void enforceWindow(String key, int limit, String action) {
        if (limit <= 0) {
            return;
        }
        Long count;
        try {
            count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, WINDOW);
            }
        } catch (RuntimeException ex) {
            log.warn("{}限流计数不可用，放行本次请求：{}", action, ex.getClass().getSimpleName());
            return;
        }
        if (count != null && count > limit) {
            log.warn("{}请求超过限流阈值 {} 次/分钟", action, limit);
            throw new BizException(ErrorCode.RATE_LIMITED);
        }
    }

    private Long readCounter(String key) {
        try {
            String value = redisTemplate.opsForValue().get(key);
            return value == null ? null : Long.valueOf(value);
        } catch (NumberFormatException ex) {
            log.warn("登录失败计数格式异常，按未锁定处理：{}", ex.getClass().getSimpleName());
            return null;
        } catch (RuntimeException ex) {
            log.warn("登录失败计数读取失败，放行本次请求：{}", ex.getClass().getSimpleName());
            return null;
        }
    }

    private static String accountKey(String account) {
        if (account == null || account.isBlank()) {
            return null;
        }
        String normalized = account.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > MAX_ACCOUNT_KEY_LENGTH) {
            normalized = normalized.substring(0, MAX_ACCOUNT_KEY_LENGTH);
        }
        return RedisKeys.loginFailAccount(normalized);
    }

    private static String mask(String account) {
        if (account == null || account.isBlank()) {
            return "-";
        }
        String normalized = account.trim().toLowerCase(Locale.ROOT);
        int separator = normalized.indexOf('@');
        if (separator <= 0) {
            return normalized.charAt(0) + "***";
        }
        return normalized.charAt(0) + "***" + normalized.substring(separator);
    }
}
