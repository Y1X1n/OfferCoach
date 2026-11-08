package com.offercoach.identity.application.quota;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offercoach.common.cache.RedisKeys;
import com.offercoach.common.error.BizException;
import com.offercoach.common.error.ErrorCode;
import com.offercoach.identity.application.port.QuotaRepository;
import com.offercoach.identity.application.settings.PlatformSettingsService;
import com.offercoach.identity.config.QuotaProperties;
import com.offercoach.identity.domain.quota.QuotaAccount;
import com.offercoach.identity.domain.quota.QuotaLedgerEntry;
import com.offercoach.identity.domain.quota.QuotaType;
import com.offercoach.identity.domain.quota.SubscriptionPlan;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 额度核心服务：初始化、快照、扣减、返还与套餐查询。
 * 余额以 DB 为准；扣减路径使用 SELECT ... FOR UPDATE 串行化同一用户。
 */
@Service
@RequiredArgsConstructor
public class QuotaService {

    private static final Logger log = LoggerFactory.getLogger(QuotaService.class);

    private static final String FREE_PLAN_CODE = "FREE";
    private static final String FREE_PLAN_NAME = "免费版";
    private static final String PLAN_GRANT_REASON = "PLAN_GRANT";
    private static final String PLAN_GRANT_REF_TYPE = "PLAN";
    private static final String PLAN_GRANT_KEY_PREFIX = "plan-grant:";
    private static final String PLAN_RENEWAL_REASON = "PLAN_RENEWAL";
    private static final String PLAN_RENEWAL_KEY_PREFIX = "plan-renewal:";
    private static final TypeReference<Map<String, Object>> FEATURE_MAP_TYPE = new TypeReference<>() {};

    private static final DateTimeFormatter DAY_KEY = DateTimeFormatter.BASIC_ISO_DATE;

    private final QuotaRepository quotaRepository;
    private final QuotaProperties properties;
    private final PlatformSettingsService settingsService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 幂等初始化：已有 user_quota 时直接返回；planCode 不存在时回退 FREE。
     * 额度行写入后在同一事务内补记初始发放流水，保证 quota_ledger 可核对。
     */
    @Transactional
    public void initializeQuota(long userId, String planCode) {
        if (quotaRepository.findQuota(userId).isPresent()) {
            return;
        }
        SubscriptionPlan plan = findPlan(planCode);
        if (plan == null) {
            plan = syntheticFreePlan();
        }
        String effectivePlanCode = plan.code() == null || plan.code().isBlank() ? FREE_PLAN_CODE : plan.code();
        LocalDate today = LocalDate.now();
        quotaRepository.insertQuotaIfAbsent(new QuotaAccount(
                userId,
                effectivePlanCode,
                plan.interviewCredits(),
                plan.resumeCredits(),
                today,
                today.plusMonths(1),
                today,
                0));
        grantPlanQuota(userId, QuotaType.INTERVIEW, plan.interviewCredits());
        grantPlanQuota(userId, QuotaType.RESUME, plan.resumeCredits());
    }

    /**
     * 记录套餐初始发放流水；余额为 0 时不产生流水（delta 不得为 0）。
     * 并发初始化撞唯一键时直接抛出，由调用方事务回滚，不在此捕获后继续。
     */
    private void grantPlanQuota(long userId, QuotaType type, int credits) {
        if (credits <= 0) {
            return;
        }
        QuotaLedgerEntry entry = new QuotaLedgerEntry(
                null,
                userId,
                type.name(),
                credits,
                credits,
                PLAN_GRANT_REASON,
                PLAN_GRANT_REF_TYPE,
                null,
                PLAN_GRANT_KEY_PREFIX + type.name() + ":" + userId);
        // 唯一键 (user_id, idempotency_key) 冲突时直接抛出，由事务回滚，不在此捕获后继续。
        quotaRepository.insertLedger(entry);
    }

    /**
     * 返回当前额度快照；行不存在时按 FREE 初始化，跨天时在同一事务内重置每日计数。
     */
    @Transactional
    public QuotaSnapshot snapshot(long userId) {
        LocalDate today = LocalDate.now();
        QuotaAccount quota = quotaRepository.findQuota(userId).orElse(null);
        if (quota == null) {
            initializeQuota(userId, FREE_PLAN_CODE);
            quota = quotaRepository.findQuota(userId).orElse(null);
        }

        if (quota != null && quota.periodEnd() != null && !today.isBefore(quota.periodEnd())) {
            renewPeriod(userId, quota, today);
            quota = quotaRepository.findQuota(userId).orElse(quota);
        }

        int sessionsUsed;
        if (quota == null) {
            log.warn("user_quota row still missing after FREE initialization, userId={}", userId);
            quota = fallbackQuota(userId, today);
            sessionsUsed = 0;
        } else if (!today.equals(quota.dailySessionDate())) {
            int reset = quotaRepository.resetDailyIfStale(userId, today);
            if (reset > 0) {
                quota = quota.withDailySession(today, 0);
            } else {
                // 并发请求已抢先重置或计数，重新读取以返回最新值。
                QuotaAccount refreshed = quotaRepository.findQuota(userId).orElse(null);
                if (refreshed != null) {
                    quota = refreshed;
                    if (quota.dailySessionDate() == null) {
                        quota = quota.withDailySession(today, quota.dailySessionCount());
                    }
                }
            }
            sessionsUsed = quota.dailySessionCount();
        } else {
            sessionsUsed = quota.dailySessionCount();
        }

        String planCode = quota.planCode() == null || quota.planCode().isBlank() ? FREE_PLAN_CODE : quota.planCode();
        SubscriptionPlan plan = findPlan(planCode);
        String planName = plan != null && plan.name() != null ? plan.name() : planCode;
        Map<String, Object> features = plan == null ? Map.of() : parseFeatures(plan.featuresJson());
        LocalDate periodEnd = quota.periodEnd() == null ? today.plusMonths(1) : quota.periodEnd();
        LocalDate dailyDate = quota.dailySessionDate() == null ? today : quota.dailySessionDate();

        return new QuotaSnapshot(
                planCode,
                planName,
                quota.interviewCredits(),
                quota.resumeCredits(),
                periodEnd,
                dailyDate,
                sessionsUsed,
                settingsService.snapshot().dailyInterviewLimit(),
                features);
    }

    /**
     * 周期到期续发：降级 FREE 并按运行时策略续发月度额度；流水幂等，可重复调用。
     */
    private void renewPeriod(long userId, QuotaAccount quota, LocalDate today) {
        PlatformSettingsService.Snapshot settings = settingsService.snapshot();
        int interviewAdd = Math.max(0, settings.freeMonthlyInterviews());
        int resumeAdd = Math.max(0, settings.freeMonthlyResumes());
        LocalDate oldPeriodEnd = quota.periodEnd();
        boolean renewed = quotaRepository.renewExpired(
                userId, FREE_PLAN_CODE, interviewAdd, resumeAdd, today, today.plusMonths(1));
        if (!renewed) {
            return;
        }
        QuotaAccount after = quotaRepository.findQuota(userId).orElse(null);
        int interviewBalance = after == null ? interviewAdd : after.interviewCredits();
        int resumeBalance = after == null ? resumeAdd : after.resumeCredits();
        insertRenewalLedger(userId, QuotaType.INTERVIEW, interviewAdd, interviewBalance, oldPeriodEnd);
        insertRenewalLedger(userId, QuotaType.RESUME, resumeAdd, resumeBalance, oldPeriodEnd);
        log.info(
                "额度周期续发：userId={} oldPeriodEnd={} interview+{} resume+{}",
                userId,
                oldPeriodEnd,
                interviewAdd,
                resumeAdd);
    }

    private void insertRenewalLedger(
            long userId, QuotaType type, int amount, int balanceAfter, LocalDate oldPeriodEnd) {
        if (amount <= 0) {
            return;
        }
        quotaRepository.insertLedgerIfAbsent(new QuotaLedgerEntry(
                null,
                userId,
                type.name(),
                amount,
                balanceAfter,
                PLAN_RENEWAL_REASON,
                PLAN_GRANT_REF_TYPE,
                null,
                PLAN_RENEWAL_KEY_PREFIX + oldPeriodEnd + ":" + type.name() + ":" + userId));
    }

    /**
     * 扣减额度；幂等键命中时返回已处理结果，不重复扣减。
     */
    @Transactional
    public QuotaResult charge(
            long userId, QuotaType type, int amount, String reason, String refType, Long refId, String idemKey) {
        return applyDelta(userId, type, amount, reason, refType, refId, idemKey, true);
    }

    /**
     * 返还额度；与扣减共用行锁与幂等逻辑。
     */
    @Transactional
    public QuotaResult refund(
            long userId, QuotaType type, int amount, String reason, String refType, Long refId, String idemKey) {
        return applyDelta(userId, type, amount, reason, refType, refId, idemKey, false);
    }

    /**
     * 发放奖励额度（邀请奖励、运营补偿）；与扣减共用行锁与幂等逻辑。
     */
    @Transactional
    public QuotaResult grant(
            long userId, QuotaType type, int amount, String reason, String refType, Long refId, String idemKey) {
        return applyDelta(userId, type, amount, reason, refType, refId, idemKey, false);
    }

    /**
     * 占用一次每日会话名额；达到上限抛 QUOTA_EXCEEDED。
     * 与创建会话同事务调用，后续失败会随事务回滚。
     */
    @Transactional
    public void reserveDailySession(long userId) {
        int cap = settingsService.snapshot().dailyInterviewLimit();
        if (cap <= 0) {
            return;
        }
        LocalDate today = LocalDate.now();
        int updated = quotaRepository.incrementDailyIfUnderCap(userId, today, cap);
        if (updated == 0) {
            throw new BizException(ErrorCode.QUOTA_EXCEEDED, Map.of("type", "DAILY_SESSION", "cap", cap));
        }
    }

    /**
     * 占用一次每日简历解析名额；Redis 故障放行，避免缓存故障阻断解析。
     */
    public void reserveDailyResumeParse(long userId) {
        int cap = settingsService.snapshot().dailyResumeLimit();
        if (cap <= 0) {
            return;
        }
        String key = RedisKeys.dailyResumeParse(userId, LocalDate.now().format(DAY_KEY));
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, Duration.ofDays(2));
            }
            if (count != null && count > cap) {
                throw new BizException(ErrorCode.QUOTA_EXCEEDED, Map.of("type", "DAILY_RESUME_PARSE", "cap", cap));
            }
        } catch (BizException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn("每日解析次数计数不可用，放行本次请求：{}", exception.getClass().getSimpleName());
        }
    }

    /**
     * 生效中的套餐，按价格升序。
     */
    @Transactional(readOnly = true)
    public List<PlanView> listPlans() {
        return quotaRepository.listActivePlans().stream()
                .map(plan -> new PlanView(
                        plan.code(),
                        plan.name(),
                        plan.priceCents(),
                        plan.periodDays(),
                        plan.interviewCredits(),
                        plan.resumeCredits(),
                        parseFeatures(plan.featuresJson()),
                        plan.active()))
                .toList();
    }

    private QuotaResult applyDelta(
            long userId,
            QuotaType type,
            int amount,
            String reason,
            String refType,
            Long refId,
            String idemKey,
            boolean deduct) {
        if (amount <= 0) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, Map.of("amount", amount));
        }
        if (idemKey == null || idemKey.isBlank()) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, Map.of("field", "idempotencyKey"));
        }
        if (reason == null || reason.isBlank()) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, Map.of("field", "reason"));
        }

        QuotaAccount quota = quotaRepository
                .lockQuota(userId)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("userId", userId)));

        QuotaLedgerEntry existing = quotaRepository.findLedger(userId, idemKey).orElse(null);
        if (existing != null) {
            return QuotaResult.alreadyProcessed(existing.balanceAfter());
        }

        int balance = balanceOf(quota, type);
        if (deduct && balance < amount) {
            throw quotaExceeded(amount, balance, quota.planCode());
        }

        int updated = deduct
                ? quotaRepository.decrease(quota.userId(), type, amount)
                : quotaRepository.increase(quota.userId(), type, amount);
        if (updated == 0) {
            if (deduct) {
                throw quotaExceeded(amount, balance, quota.planCode());
            }
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("userId", userId));
        }

        int balanceAfter = deduct ? balance - amount : balance + amount;
        QuotaLedgerEntry ledger = new QuotaLedgerEntry(
                null, userId, type.name(), deduct ? -amount : amount, balanceAfter, reason, refType, refId, idemKey);
        // 唯一键冲突直接抛出并回滚，不在事务内捕获后继续查询。
        quotaRepository.insertLedger(ledger);

        evictQuotaCache(userId);
        return QuotaResult.applied(balanceAfter);
    }

    private static int balanceOf(QuotaAccount quota, QuotaType type) {
        return switch (type) {
            case INTERVIEW -> quota.interviewCredits();
            case RESUME -> quota.resumeCredits();
        };
    }

    private static BizException quotaExceeded(int required, int balance, String planCode) {
        return new BizException(
                ErrorCode.QUOTA_EXCEEDED,
                Map.of(
                        "required", required,
                        "balance", balance,
                        "planCode", planCode == null ? FREE_PLAN_CODE : planCode));
    }

    /**
     * Redis 只做快速拒绝缓存；清理失败只告警，绝不影响已提交的 DB 结果。
     */
    private void evictQuotaCache(long userId) {
        try {
            redisTemplate.delete(RedisKeys.quotaCache(userId));
        } catch (RuntimeException exception) {
            log.warn("failed to evict quota cache, userId={}", userId, exception);
        }
    }

    private SubscriptionPlan findPlan(String planCode) {
        if (planCode != null && !planCode.isBlank()) {
            SubscriptionPlan plan = quotaRepository.findPlan(planCode.trim()).orElse(null);
            if (plan != null) {
                return plan;
            }
        }
        return quotaRepository.findPlan(FREE_PLAN_CODE).orElse(null);
    }

    private Map<String, Object> parseFeatures(String featuresJson) {
        if (featuresJson == null || featuresJson.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(featuresJson, FEATURE_MAP_TYPE);
            return parsed == null ? Map.of() : parsed;
        } catch (JsonProcessingException exception) {
            log.warn("invalid subscription_plan.features json, fallback to empty map", exception);
            return Map.of();
        }
    }

    private SubscriptionPlan syntheticFreePlan() {
        return new SubscriptionPlan(
                FREE_PLAN_CODE,
                FREE_PLAN_NAME,
                0,
                30,
                properties.getFreeMonthlyInterviews(),
                properties.getFreeMonthlyResumes(),
                "{}",
                true);
    }

    private QuotaAccount fallbackQuota(long userId, LocalDate today) {
        SubscriptionPlan plan = findPlan(FREE_PLAN_CODE);
        if (plan == null) {
            plan = syntheticFreePlan();
        }
        return new QuotaAccount(
                userId,
                plan.code(),
                plan.interviewCredits(),
                plan.resumeCredits(),
                today,
                today.plusMonths(1),
                today,
                0);
    }
}
