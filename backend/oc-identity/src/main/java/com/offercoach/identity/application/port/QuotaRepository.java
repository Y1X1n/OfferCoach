package com.offercoach.identity.application.port;

import com.offercoach.identity.domain.quota.QuotaAccount;
import com.offercoach.identity.domain.quota.QuotaLedgerEntry;
import com.offercoach.identity.domain.quota.QuotaType;
import com.offercoach.identity.domain.quota.SubscriptionPlan;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 额度仓储端口；事务边界由应用服务持有。
 */
public interface QuotaRepository {

    /**
     * 读取用户额度行，不加锁。
     */
    Optional<QuotaAccount> findQuota(long userId);

    /**
     * SELECT ... FOR UPDATE 读取；同一用户的扣减 / 返还由此串行化。
     */
    Optional<QuotaAccount> lockQuota(long userId);

    /**
     * 幂等插入额度行；主键冲突时不做任何变更。
     */
    void insertQuotaIfAbsent(QuotaAccount quota);

    /**
     * 条件扣减；余额不足时影响 0 行。
     */
    int decrease(long userId, QuotaType type, int amount);

    /**
     * 增加额度。
     */
    int increase(long userId, QuotaType type, int amount);

    /**
     * 跨天重置每日会话计数；已重置过则返回 0。
     */
    int resetDailyIfStale(long userId, LocalDate today);

    /**
     * 周期到期续发：重置周期与每日计数，并追加减额度；未到期返回 false。
     */
    boolean renewExpired(
            long userId, String planCode, int interviewAdd, int resumeAdd, LocalDate today, LocalDate nextPeriodEnd);

    /**
     * 幂等追加流水，冲突时忽略。
     */
    void insertLedgerIfAbsent(QuotaLedgerEntry entry);

    /**
     * 原子占用每日会话名额；达到上限返回 0。
     */
    int incrementDailyIfUnderCap(long userId, LocalDate today, int cap);

    /**
     * 按幂等键查流水；调用方必须已持有 user_quota 行锁。
     */
    Optional<QuotaLedgerEntry> findLedger(long userId, String idemKey);

    /**
     * 追加流水；唯一键冲突直接抛出，由调用方事务回滚。
     */
    void insertLedger(QuotaLedgerEntry entry);

    /**
     * 按 code 查套餐，包含未生效的套餐。
     */
    Optional<SubscriptionPlan> findPlan(String code);

    /**
     * 生效中的套餐，按价格升序。
     */
    List<SubscriptionPlan> listActivePlans();
}
