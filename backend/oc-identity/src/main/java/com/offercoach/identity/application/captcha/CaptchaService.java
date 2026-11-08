package com.offercoach.identity.application.captcha;

import com.offercoach.common.cache.RedisKeys;
import com.offercoach.common.error.BizException;
import com.offercoach.common.error.ErrorCode;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 注册算术验证码：一次性挑战，Redis 保存答案 5 分钟；Redis 不可用时拒绝注册而非放行。
 */
@Service
public class CaptchaService {

    private static final Logger log = LoggerFactory.getLogger(CaptchaService.class);

    private static final Duration TTL = Duration.ofMinutes(5);
    private static final int ISSUE_PER_HOUR = 120;
    private static final DateTimeFormatter HOUR_KEY = DateTimeFormatter.ofPattern("yyyyMMddHH");
    private static final String ISSUE_KEY_PREFIX = "oc:auth:captcha-issue:";
    private static final char[] OPERATORS = {'+', '-', '×'};

    private final StringRedisTemplate redisTemplate;
    private final SecureRandom random = new SecureRandom();

    public CaptchaService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 生成挑战：返回 captchaId 与题面，答案只存 Redis。
     */
    public Challenge issue(String ip) {
        enforceIssueLimit(ip);
        int left = 1 + random.nextInt(9);
        int right = 1 + random.nextInt(9);
        char operator = OPERATORS[random.nextInt(OPERATORS.length)];
        if (operator == '-' && right > left) {
            int swap = left;
            left = right;
            right = swap;
        }
        int answer =
                switch (operator) {
                    case '+' -> left + right;
                    case '-' -> left - right;
                    default -> left * right;
                };
        String captchaId = randomId();
        try {
            redisTemplate.opsForValue().set(RedisKeys.captcha(captchaId), Integer.toString(answer), TTL);
        } catch (RuntimeException exception) {
            log.warn("验证码写入失败：{}", exception.getClass().getSimpleName());
            throw new BizException(ErrorCode.INTERNAL_ERROR, Map.of("reason", "CAPTCHA_UNAVAILABLE"));
        }
        return new Challenge(captchaId, left + " " + operator + " " + right + " = ?", TTL.toSeconds());
    }

    /**
     * 校验并消费挑战：失败可重试一次需重新获取，防止穷举。
     */
    public void verify(String captchaId, String captchaAnswer) {
        if (captchaId == null || captchaId.isBlank()) {
            throw fieldError("captchaAnswer", "请先完成验证码");
        }
        if (captchaAnswer == null || captchaAnswer.isBlank()) {
            throw fieldError("captchaAnswer", "请填写验证码答案");
        }
        String expected;
        try {
            expected = redisTemplate.opsForValue().getAndDelete(RedisKeys.captcha(captchaId.trim()));
        } catch (RuntimeException exception) {
            log.warn("验证码校验失败：{}", exception.getClass().getSimpleName());
            throw new BizException(ErrorCode.INTERNAL_ERROR, Map.of("reason", "CAPTCHA_UNAVAILABLE"));
        }
        if (expected == null) {
            throw fieldError("captchaAnswer", "验证码已过期，请刷新后重试");
        }
        if (!expected.equals(captchaAnswer.trim())) {
            throw fieldError("captchaAnswer", "验证码不正确");
        }
    }

    private void enforceIssueLimit(String ip) {
        String key = ISSUE_KEY_PREFIX + (ip == null || ip.isBlank() ? "unknown" : ip) + ":"
                + LocalDateTime.now().format(HOUR_KEY);
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, Duration.ofHours(2));
            }
            if (count != null && count > ISSUE_PER_HOUR) {
                throw new BizException(ErrorCode.RATE_LIMITED, Map.of("limit", ISSUE_PER_HOUR, "window", "1h"));
            }
        } catch (BizException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn("验证码签发限流不可用，本次放行：{}", exception.getClass().getSimpleName());
        }
    }

    private String randomId() {
        byte[] bytes = new byte[9];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static BizException fieldError(String field, String message) {
        return new BizException(ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of(field, message)));
    }

    public record Challenge(String captchaId, String question, long expiresIn) {}
}
