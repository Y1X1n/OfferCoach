package com.offercoach.identity.application.subscription;

import com.offercoach.identity.application.port.SubscriptionRepository;
import com.offercoach.identity.application.quota.QuotaService;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 到期结算的单用户事务边界。
 *
 * <p>独立成 Bean 的原因：{@link SubscriptionExpiryJob} 需要「每个用户一条事务」，
 * 若写成同类私有方法，{@code @Transactional} 不会经过代理而失效；同时这样能把
 * 单用户失败隔离，不影响同批其他用户。</p>
 */
@Service
public class SubscriptionExpirySettlement {

    private final SubscriptionRepository subscriptionRepository;
    private final QuotaService quotaService;

    public SubscriptionExpirySettlement(SubscriptionRepository subscriptionRepository, QuotaService quotaService) {
        this.subscriptionRepository = subscriptionRepository;
        this.quotaService = quotaService;
    }

    /**
     * 把该用户已过期的 ACTIVE 订阅置为 EXPIRED；返回是否改动。
     */
    @Transactional
    public boolean expireSubscription(long userId) {
        return subscriptionRepository.expireOverdue(userId, Instant.now());
    }

    /**
     * 结算该用户已到期的额度周期；返回是否续发。
     */
    @Transactional
    public boolean renewQuota(long userId) {
        return quotaService.renewIfExpired(userId);
    }
}
