package com.offercoach.identity.application;

import com.offercoach.common.error.BizException;
import com.offercoach.common.error.ErrorCode;
import com.offercoach.identity.application.port.UserRepository;
import com.offercoach.identity.application.quota.QuotaService;
import com.offercoach.identity.application.quota.QuotaSnapshot;
import com.offercoach.identity.domain.user.UserAccount;
import java.time.DateTimeException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 当前用户资料查询与更新。
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private static final String PLAN_FREE = "FREE";
    private static final String HTTPS_PREFIX = "https://";

    private final UserRepository userRepository;
    private final QuotaService quotaService;

    public UserService(UserRepository userRepository, QuotaService quotaService) {
        this.userRepository = userRepository;
        this.quotaService = quotaService;
    }

    public UserProfile me(long userId) {
        return toProfile(requireUser(userId));
    }

    @Transactional
    public UserProfile update(
            long userId, String nickname, String timezone, String avatarUrl, Boolean allowRawResumeToLlm) {
        UserAccount user = requireUser(userId);

        String normalizedNickname = validateNickname(nickname);
        String normalizedTimezone = validateTimezone(timezone);
        String normalizedAvatarUrl = validateAvatarUrl(avatarUrl);

        if (normalizedNickname != null || normalizedTimezone != null || normalizedAvatarUrl != null) {
            userRepository.updateProfile(
                    user.id(),
                    normalizedNickname,
                    normalizedTimezone,
                    normalizedAvatarUrl,
                    OffsetDateTime.now(ZoneOffset.UTC));
            user = requireUser(userId);
        }
        if (allowRawResumeToLlm != null) {
            userRepository.updateAllowRawResumeToLlm(user.id(), allowRawResumeToLlm);
        }
        return toProfile(user);
    }

    private UserAccount requireUser(long userId) {
        return userRepository
                .findById(userId)
                .filter(user -> user.deletedAt() == null)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private UserProfile toProfile(UserAccount user) {
        return new UserProfile(
                user.id(),
                user.email(),
                user.nickname(),
                user.avatarUrl(),
                user.role(),
                user.timezone(),
                planCodeOf(user.id()),
                user.createdAt(),
                userRepository.allowRawResumeToLlm(user.id()));
    }

    private String planCodeOf(long userId) {
        try {
            QuotaSnapshot snapshot = quotaService.snapshot(userId);
            if (snapshot != null
                    && snapshot.planCode() != null
                    && !snapshot.planCode().isBlank()) {
                return snapshot.planCode();
            }
        } catch (RuntimeException ex) {
            log.warn("用户 {} 套餐查询失败，回退 FREE：{}", userId, ex.getClass().getSimpleName());
        }
        return PLAN_FREE;
    }

    private String validateNickname(String nickname) {
        if (nickname == null) {
            return null;
        }
        String trimmed = nickname.trim();
        if (trimmed.length() < 2 || trimmed.length() > 40) {
            throw fieldError("nickname", "昵称长度需为 2-40");
        }
        return trimmed;
    }

    private String validateTimezone(String timezone) {
        if (timezone == null) {
            return null;
        }
        String trimmed = timezone.trim();
        if (trimmed.isEmpty()) {
            throw fieldError("timezone", "时区不合法");
        }
        try {
            ZoneId.of(trimmed);
        } catch (DateTimeException ex) {
            throw fieldError("timezone", "时区不合法");
        }
        return trimmed;
    }

    private String validateAvatarUrl(String avatarUrl) {
        if (avatarUrl == null) {
            return null;
        }
        String trimmed = avatarUrl.trim();
        if (!trimmed.toLowerCase(Locale.ROOT).startsWith(HTTPS_PREFIX) || trimmed.length() <= HTTPS_PREFIX.length()) {
            throw fieldError("avatarUrl", "头像地址必须使用 https");
        }
        return trimmed;
    }

    private static BizException fieldError(String field, String message) {
        return new BizException(ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.<String, Object>of(field, message)));
    }

    /**
     * 应用层用户资料；由 Controller 转换为 MeResponse。
     */
    public record UserProfile(
            long id,
            String email,
            String nickname,
            String avatarUrl,
            String role,
            String timezone,
            String planCode,
            OffsetDateTime createdAt,
            boolean allowRawResumeToLlm) {}
}
