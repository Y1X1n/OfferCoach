package com.offercoach.identity.application.port;

import java.time.Instant;
import java.util.List;

/**
 * 订阅仓储端口；购买成功后写入一条 ACTIVE 订阅。
 */
public interface SubscriptionRepository {

    /**
     * 结束当前生效订阅；无生效订阅时影响 0 行。
     */
    void expireActive(long userId);

    /**
     * 写入新的生效订阅。
     */
    void insertActive(long userId, String planCode, Instant startedAt, Instant expiresAt);

    /**
     * 把该用户已过 {@code expires_at} 的 ACTIVE 订阅置为 EXPIRED；返回是否真的改动。
     *
     * <p>「买新套餐替换」走 {@link #expireActive}，自然到期走这条。</p>
     */
    boolean expireOverdue(long userId, Instant now);

    /**
     * 找出已到期但仍为 ACTIVE 的用户，供定时任务分批结算。
     */
    List<Long> findExpiredActiveUserIds(Instant now, int limit);
}
