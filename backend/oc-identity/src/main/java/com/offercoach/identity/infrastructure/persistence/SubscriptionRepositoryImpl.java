package com.offercoach.identity.infrastructure.persistence;

import com.offercoach.identity.application.port.SubscriptionRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 订阅仓储实现。
 */
@Repository
@RequiredArgsConstructor
public class SubscriptionRepositoryImpl implements SubscriptionRepository {

    private final UserSubscriptionMapper mapper;

    @Override
    public void expireActive(long userId) {
        mapper.expireActive(userId);
    }

    @Override
    public void insertActive(long userId, String planCode, Instant startedAt, Instant expiresAt) {
        mapper.insertActive(userId, planCode, toOffset(startedAt), toOffset(expiresAt));
    }

    private static OffsetDateTime toOffset(Instant value) {
        return value == null ? null : OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }
}
