package com.offercoach.identity.application.port;

import java.time.Instant;

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
}
