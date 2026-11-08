package com.offercoach.identity.infrastructure.persistence;

import com.offercoach.identity.application.port.QuotaRepository;
import com.offercoach.identity.domain.quota.QuotaAccount;
import com.offercoach.identity.domain.quota.QuotaLedgerEntry;
import com.offercoach.identity.domain.quota.QuotaType;
import com.offercoach.identity.domain.quota.SubscriptionPlan;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 额度仓储实现：包装三个 Mapper，并在边界完成 DO 与领域 record 的映射。
 */
@Repository
@RequiredArgsConstructor
public class QuotaRepositoryImpl implements QuotaRepository {

    private final UserQuotaMapper quotaMapper;
    private final QuotaLedgerMapper ledgerMapper;
    private final SubscriptionPlanMapper planMapper;

    @Override
    public Optional<QuotaAccount> findQuota(long userId) {
        return Optional.ofNullable(toAccount(quotaMapper.selectById(userId)));
    }

    @Override
    public Optional<QuotaAccount> lockQuota(long userId) {
        return Optional.ofNullable(toAccount(quotaMapper.selectForUpdate(userId)));
    }

    @Override
    public void insertQuotaIfAbsent(QuotaAccount quota) {
        quotaMapper.insertIfAbsent(
                quota.userId(),
                quota.planCode(),
                quota.interviewCredits(),
                quota.resumeCredits(),
                quota.periodStart(),
                quota.periodEnd(),
                quota.dailySessionDate());
    }

    @Override
    public int decrease(long userId, QuotaType type, int amount) {
        return switch (type) {
            case INTERVIEW -> quotaMapper.decreaseInterview(userId, amount);
            case RESUME -> quotaMapper.decreaseResume(userId, amount);
        };
    }

    @Override
    public int increase(long userId, QuotaType type, int amount) {
        return switch (type) {
            case INTERVIEW -> quotaMapper.increaseInterview(userId, amount);
            case RESUME -> quotaMapper.increaseResume(userId, amount);
        };
    }

    @Override
    public int resetDailyIfStale(long userId, LocalDate today) {
        return quotaMapper.resetDailyIfStale(userId, today);
    }

    @Override
    public boolean renewExpired(
            long userId, String planCode, int interviewAdd, int resumeAdd, LocalDate today, LocalDate nextPeriodEnd) {
        return quotaMapper.renewExpired(userId, planCode, interviewAdd, resumeAdd, today, nextPeriodEnd) > 0;
    }

    @Override
    public void insertLedgerIfAbsent(QuotaLedgerEntry entry) {
        ledgerMapper.insertIfAbsent(
                entry.userId(),
                entry.quotaType(),
                entry.delta(),
                entry.balanceAfter(),
                entry.reason(),
                entry.refType(),
                entry.refId(),
                entry.idempotencyKey());
    }

    @Override
    public int incrementDailyIfUnderCap(long userId, LocalDate today, int cap) {
        return quotaMapper.incrementDailyIfUnderCap(userId, today, cap);
    }

    @Override
    public Optional<QuotaLedgerEntry> findLedger(long userId, String idemKey) {
        return Optional.ofNullable(toLedger(ledgerMapper.findByIdemKey(userId, idemKey)));
    }

    @Override
    public void insertLedger(QuotaLedgerEntry entry) {
        QuotaLedgerDO row = new QuotaLedgerDO();
        row.setId(entry.id());
        row.setUserId(entry.userId());
        row.setQuotaType(entry.quotaType());
        row.setDelta(entry.delta());
        row.setBalanceAfter(entry.balanceAfter());
        row.setReason(entry.reason());
        row.setRefType(entry.refType());
        row.setRefId(entry.refId());
        row.setIdempotencyKey(entry.idempotencyKey());
        ledgerMapper.insert(row);
    }

    @Override
    public Optional<SubscriptionPlan> findPlan(String code) {
        return Optional.ofNullable(toPlan(planMapper.findByCode(code)));
    }

    @Override
    public List<SubscriptionPlan> listActivePlans() {
        return planMapper.listActive().stream().map(QuotaRepositoryImpl::toPlan).toList();
    }

    private static QuotaAccount toAccount(UserQuotaDO row) {
        if (row == null) {
            return null;
        }
        return new QuotaAccount(
                row.getUserId() == null ? 0L : row.getUserId(),
                row.getPlanCode(),
                zero(row.getInterviewCredits()),
                zero(row.getResumeCredits()),
                row.getPeriodStart(),
                row.getPeriodEnd(),
                row.getDailySessionDate(),
                zero(row.getDailySessionCount()));
    }

    private static QuotaLedgerEntry toLedger(QuotaLedgerDO row) {
        if (row == null) {
            return null;
        }
        return new QuotaLedgerEntry(
                row.getId(),
                row.getUserId() == null ? 0L : row.getUserId(),
                row.getQuotaType(),
                zero(row.getDelta()),
                zero(row.getBalanceAfter()),
                row.getReason(),
                row.getRefType(),
                row.getRefId(),
                row.getIdempotencyKey());
    }

    private static SubscriptionPlan toPlan(SubscriptionPlanDO row) {
        if (row == null) {
            return null;
        }
        return new SubscriptionPlan(
                row.getCode(),
                row.getName(),
                zero(row.getPriceCents()),
                zero(row.getPeriodDays()),
                zero(row.getInterviewCredits()),
                zero(row.getResumeCredits()),
                row.getFeatures(),
                Boolean.TRUE.equals(row.getActive()));
    }

    private static int zero(Integer value) {
        return value == null ? 0 : value;
    }
}
