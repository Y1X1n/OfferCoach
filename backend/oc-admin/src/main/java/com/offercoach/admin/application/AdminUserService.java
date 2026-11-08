package com.offercoach.admin.application;

import com.offercoach.admin.application.model.AdminModels.Paged;
import com.offercoach.admin.application.model.AdminModels.QuotaAdjustment;
import com.offercoach.admin.application.model.AdminModels.User;
import com.offercoach.admin.application.port.AdminUserRepository;
import com.offercoach.admin.application.support.PageQuery;
import com.offercoach.admin.audit.AuditLogger;
import com.offercoach.common.cache.RedisKeys;
import com.offercoach.common.error.BizException;
import com.offercoach.common.error.ErrorCode;
import com.offercoach.identity.application.RefreshTokenService;
import com.offercoach.identity.application.quota.QuotaResult;
import com.offercoach.identity.application.quota.QuotaService;
import com.offercoach.identity.application.quota.QuotaSnapshot;
import com.offercoach.identity.domain.account.PasswordPolicy;
import com.offercoach.identity.domain.quota.QuotaType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理端用户与额度：用户检索、状态管控、补偿/扣减额度。
 */
@Service
public class AdminUserService {

    private static final Logger log = LoggerFactory.getLogger(AdminUserService.class);
    private static final Set<String> USER_STATUSES = Set.of("ACTIVE", "LOCKED", "DISABLED", "DELETED");
    private static final Set<String> EDITABLE_STATUSES = Set.of("ACTIVE", "LOCKED", "DISABLED");
    private static final Set<String> ROLES = Set.of("USER", "SUPPORT", "ADMIN");
    private static final String ACTION_STATUS = "USER_STATUS_UPDATE";
    private static final String ACTION_ROLE = "USER_ROLE_UPDATE";
    private static final String ACTION_UNLOCK = "USER_UNLOCK";
    private static final String ACTION_PASSWORD = "USER_PASSWORD_RESET";
    private static final String ACTION_QUOTA = "USER_QUOTA_ADJUST";
    private static final String TARGET_USER = "USER";
    private static final String PLAN_FREE = "FREE";
    private static final String REASON_ADMIN = "ADMIN_COMPENSATION";
    private static final String REF_TYPE_ADMIN = "ADMIN";
    private static final int REASON_MAX_LENGTH = 32;
    private static final int KEY_MAX_LENGTH = 128;

    private final AdminUserRepository userRepository;
    private final QuotaService quotaService;
    private final AuditLogger auditLogger;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;
    private final RefreshTokenService refreshTokenService;

    public AdminUserService(
            AdminUserRepository userRepository,
            QuotaService quotaService,
            AuditLogger auditLogger,
            PasswordEncoder passwordEncoder,
            StringRedisTemplate redisTemplate,
            RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.quotaService = quotaService;
        this.auditLogger = auditLogger;
        this.passwordEncoder = passwordEncoder;
        this.redisTemplate = redisTemplate;
        this.refreshTokenService = refreshTokenService;
    }

    public Paged<User> list(int page, int size, String keyword, String status) {
        PageQuery query = PageQuery.of(page, size);
        String keywordFilter = blankToNull(keyword);
        String statusFilter = normalizeStatusFilter(status);
        List<User> items = userRepository.search(keywordFilter, statusFilter, (int) query.offset(), query.size());
        long total = userRepository.count(keywordFilter, statusFilter);
        return new Paged<>(items, total, query.page(), query.size());
    }

    @Transactional
    public User updateStatus(long actorId, long userId, String status) {
        String normalized = normalizeEditableStatus(status);
        if (actorId == userId && !"ACTIVE".equals(normalized)) {
            throw new BizException(ErrorCode.FORBIDDEN, Map.of("reason", "管理员不能停用自己"));
        }
        User before = requireUser(userId);
        if (normalized.equals(before.status())) {
            return before;
        }
        int updated = userRepository.updateStatus(userId, normalized);
        if (updated == 0) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("userId", userId));
        }
        User after = requireUser(userId);
        Map<String, Object> beforeAudit = Map.of("status", before.status());
        Map<String, Object> afterAudit = Map.of("status", after.status());
        auditLogger.log(actorId, ACTION_STATUS, TARGET_USER, Long.toString(userId), beforeAudit, afterAudit);
        clearLoginFailures(after.email());
        if (!"ACTIVE".equals(normalized)) {
            revokeSessions(userId);
        }
        return after;
    }

    /**
     * 调整角色；禁止修改自己，禁止撤销最后一个管理员。
     */
    @Transactional
    public User updateRole(long actorId, long userId, String role) {
        String normalized = normalizeRole(role);
        if (actorId == userId) {
            throw new BizException(ErrorCode.FORBIDDEN, Map.of("reason", "管理员不能修改自己的角色"));
        }
        User before = requireUser(userId);
        if (normalized.equals(before.role())) {
            return before;
        }
        if ("ADMIN".equals(before.role()) && !"ADMIN".equals(normalized) && userRepository.countAdmins() <= 1) {
            throw new BizException(ErrorCode.STATE_CONFLICT, Map.of("reason", "至少保留一个管理员"));
        }
        if (userRepository.updateRole(userId, normalized) == 0) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("userId", userId));
        }
        User after = requireUser(userId);
        auditLogger.log(
                actorId,
                ACTION_ROLE,
                TARGET_USER,
                Long.toString(userId),
                Map.of("role", before.role()),
                Map.of("role", after.role()));
        revokeSessions(userId);
        return after;
    }

    /**
     * 解除登录锁定并清空失败计数；已停用/注销账号需先恢复状态。
     */
    @Transactional
    public User unlock(long actorId, long userId) {
        User before = requireUser(userId);
        if ("DISABLED".equals(before.status()) || "DELETED".equals(before.status())) {
            throw new BizException(ErrorCode.STATE_CONFLICT, Map.of("status", before.status()));
        }
        if (userRepository.unlock(userId) == 0) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("userId", userId));
        }
        User after = requireUser(userId);
        auditLogger.log(
                actorId,
                ACTION_UNLOCK,
                TARGET_USER,
                Long.toString(userId),
                Map.of("status", before.status()),
                Map.of("status", after.status()));
        clearLoginFailures(after.email());
        return after;
    }

    /**
     * 重置密码；只记录动作，不记录新旧密码。
     */
    @Transactional
    public void resetPassword(long actorId, long userId, String password) {
        if (!PasswordPolicy.isValid(password)) {
            throw fieldError("password", "密码需为 8-64 位且包含字母和数字");
        }
        User before = requireUser(userId);
        if (userRepository.updatePassword(userId, passwordEncoder.encode(password)) == 0) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("userId", userId));
        }
        userRepository.unlock(userId);
        clearLoginFailures(before.email());
        revokeSessions(userId);
        auditLogger.log(actorId, ACTION_PASSWORD, TARGET_USER, Long.toString(userId), null, null);
    }

    private void clearLoginFailures(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 320) {
            normalized = normalized.substring(0, 320);
        }
        try {
            redisTemplate.delete(RedisKeys.loginFailAccount(normalized));
        } catch (RuntimeException exception) {
            log.warn("清理登录失败计数失败：reason={}", exception.getClass().getSimpleName());
        }
    }

    private void revokeSessions(long userId) {
        try {
            refreshTokenService.revokeAllForUser(userId);
        } catch (RuntimeException exception) {
            log.warn(
                    "吊销用户 Refresh 会话失败：userId={} reason={}",
                    userId,
                    exception.getClass().getSimpleName());
        }
    }

    /**
     * 正数 delta 走补偿（refund），负数走扣减（charge）；幂等键由额度服务按用户唯一。
     */
    @Transactional
    public QuotaAdjustment adjustQuota(
            long actorId, long userId, String type, int delta, String reason, String idempotencyKey) {
        QuotaType quotaType = parseQuotaType(type);
        if (delta == 0 || delta == Integer.MIN_VALUE) {
            throw fieldError("delta", "不能为 0");
        }
        String normalizedReason = requireReason(reason);
        String normalizedKey = requireIdempotencyKey(idempotencyKey);
        requireUser(userId);

        quotaService.initializeQuota(userId, PLAN_FREE);
        QuotaSnapshot before = quotaService.snapshot(userId);
        int balanceBefore = quotaType == QuotaType.INTERVIEW ? before.interviewCredits() : before.resumeCredits();

        QuotaResult result;
        if (delta > 0) {
            result =
                    quotaService.refund(userId, quotaType, delta, REASON_ADMIN, REF_TYPE_ADMIN, actorId, normalizedKey);
        } else {
            result = quotaService.charge(
                    userId, quotaType, -delta, REASON_ADMIN, REF_TYPE_ADMIN, actorId, normalizedKey);
        }

        Map<String, Object> beforeAudit = new LinkedHashMap<>();
        beforeAudit.put("type", quotaType.name());
        beforeAudit.put("balance", balanceBefore);
        Map<String, Object> afterAudit = new LinkedHashMap<>();
        afterAudit.put("type", quotaType.name());
        afterAudit.put("delta", delta);
        afterAudit.put("reason", normalizedReason);
        afterAudit.put("idempotencyKey", normalizedKey);
        afterAudit.put("balanceAfter", result.balanceAfter());
        afterAudit.put("replayed", result.replayed());
        auditLogger.log(actorId, ACTION_QUOTA, TARGET_USER, Long.toString(userId), beforeAudit, afterAudit);
        return new QuotaAdjustment(quotaType.name(), delta, result.balanceAfter(), result.replayed());
    }

    private User requireUser(long userId) {
        return userRepository
                .findById(userId)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("userId", userId)));
    }

    private static QuotaType parseQuotaType(String type) {
        String normalized = type == null ? null : type.strip().toUpperCase(Locale.ROOT);
        try {
            return QuotaType.valueOf(normalized == null ? "" : normalized);
        } catch (IllegalArgumentException exception) {
            throw fieldError("type", "取值必须为 INTERVIEW/RESUME");
        }
    }

    private static String requireReason(String reason) {
        String normalized = reason == null ? null : reason.strip();
        if (normalized == null || normalized.isEmpty()) {
            throw fieldError("reason", "不能为空");
        }
        if (normalized.length() > REASON_MAX_LENGTH) {
            throw fieldError("reason", "长度不能超过 " + REASON_MAX_LENGTH);
        }
        return normalized;
    }

    private static String requireIdempotencyKey(String idempotencyKey) {
        String normalized = idempotencyKey == null ? null : idempotencyKey.strip();
        if (normalized == null || normalized.isEmpty()) {
            throw fieldError("idempotencyKey", "不能为空");
        }
        if (normalized.length() > KEY_MAX_LENGTH) {
            throw fieldError("idempotencyKey", "长度不能超过 " + KEY_MAX_LENGTH);
        }
        return normalized;
    }

    private static String normalizeRole(String role) {
        String normalized = role == null ? null : role.strip().toUpperCase(Locale.ROOT);
        if (normalized == null || !ROLES.contains(normalized)) {
            throw fieldError("role", "仅支持 USER/SUPPORT/ADMIN");
        }
        return normalized;
    }

    private static String normalizeStatusFilter(String status) {
        String normalized =
                status == null || status.isBlank() ? null : status.strip().toUpperCase(Locale.ROOT);
        if (normalized == null) {
            return null;
        }
        if (!USER_STATUSES.contains(normalized)) {
            throw fieldError("status", "取值必须为 ACTIVE/LOCKED/DISABLED/DELETED");
        }
        return normalized;
    }

    private static String normalizeEditableStatus(String status) {
        String normalized = status == null ? null : status.strip().toUpperCase(Locale.ROOT);
        if (normalized == null || !EDITABLE_STATUSES.contains(normalized)) {
            throw fieldError("status", "仅支持 ACTIVE/LOCKED/DISABLED");
        }
        return normalized;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static BizException fieldError(String field, String message) {
        return new BizException(ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of(field, message)));
    }
}
