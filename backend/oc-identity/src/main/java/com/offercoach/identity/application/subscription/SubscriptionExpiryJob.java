package com.offercoach.identity.application.subscription;

import com.offercoach.common.trace.JobTrace;
import com.offercoach.identity.application.port.QuotaRepository;
import com.offercoach.identity.application.port.SubscriptionRepository;
import com.offercoach.identity.application.quota.QuotaService;
import java.time.Instant;
import java.util.List;
import java.util.function.LongPredicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 订阅与额度周期到期结算。
 *
 * <p>此前到期只靠 {@link QuotaService#snapshot} 懒触发：用户不登录就不会结算，而
 * {@code user_subscription} 的 ACTIVE 行更是从没人扫过——一个 PRO 用户订阅到期后，
 * 订阅表仍是 ACTIVE、额度表却已降级 FREE，两张表长期互相矛盾。这里每天兜底扫一次。</p>
 *
 * <p>每个用户一条独立事务（见 {@link SubscriptionExpirySettlement}）：单条失败只影响自己，
 * 不会让整批回滚，下一轮会重试。</p>
 */
@Service
public class SubscriptionExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionExpiryJob.class);

    /** 每批处理的用户数；配合索引只扫到期区间。 */
    private static final int BATCH_SIZE = 200;

    /** 单轮最多处理多少批，避免异常数据把一次调度拖成长时间占用。 */
    private static final int MAX_BATCHES = 50;

    private final SubscriptionRepository subscriptionRepository;
    private final QuotaRepository quotaRepository;
    private final SubscriptionExpirySettlement settlement;

    public SubscriptionExpiryJob(
            SubscriptionRepository subscriptionRepository,
            QuotaRepository quotaRepository,
            SubscriptionExpirySettlement settlement) {
        this.subscriptionRepository = subscriptionRepository;
        this.quotaRepository = quotaRepository;
        this.settlement = settlement;
    }

    /**
     * 每天 03:10（北京时间）结算到期订阅与额度周期。
     */
    @Scheduled(cron = "${oc.identity.subscription-expiry-cron:0 10 3 * * *}", zone = "Asia/Shanghai")
    public void run() {
        try (JobTrace ignored = JobTrace.open("subscription-expiry")) {
            int expiredSubscriptions = expireSubscriptions();
            int renewedQuotas = renewQuotas();
            if (expiredSubscriptions > 0 || renewedQuotas > 0) {
                log.info("订阅到期结算完成：expiredSubscriptions={} renewedQuotas={}", expiredSubscriptions, renewedQuotas);
            }
        }
    }

    /**
     * 把已过期的 ACTIVE 订阅置为 EXPIRED。
     */
    private int expireSubscriptions() {
        int total = 0;
        for (int batch = 0; batch < MAX_BATCHES; batch++) {
            List<Long> userIds = subscriptionRepository.findExpiredActiveUserIds(Instant.now(), BATCH_SIZE);
            if (userIds.isEmpty()) {
                break;
            }
            int changed = applyBatch(userIds, settlement::expireSubscription, "订阅到期置 EXPIRED");
            total += changed;
            // 整批都没有推进说明这些行已无法被本任务改动，跳出避免空转。
            if (changed == 0) {
                break;
            }
        }
        return total;
    }

    /**
     * 结算已到期但用户长期未登录的额度周期。
     */
    private int renewQuotas() {
        int total = 0;
        for (int batch = 0; batch < MAX_BATCHES; batch++) {
            List<Long> userIds = quotaRepository.findExpiredPeriodUserIds(QuotaService.businessDate(), BATCH_SIZE);
            if (userIds.isEmpty()) {
                break;
            }
            int changed = applyBatch(userIds, settlement::renewQuota, "额度周期续发");
            total += changed;
            if (changed == 0) {
                break;
            }
        }
        return total;
    }

    private int applyBatch(List<Long> userIds, LongPredicate action, String label) {
        int changed = 0;
        for (Long userId : userIds) {
            try {
                if (action.test(userId)) {
                    changed++;
                }
            } catch (RuntimeException exception) {
                log.warn(
                        "{}失败，等待下轮重试：userId={} reason={}",
                        label,
                        userId,
                        exception.getClass().getSimpleName());
            }
        }
        return changed;
    }
}
