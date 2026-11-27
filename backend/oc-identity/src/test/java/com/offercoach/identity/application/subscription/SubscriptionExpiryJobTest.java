package com.offercoach.identity.application.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.offercoach.identity.application.port.QuotaRepository;
import com.offercoach.identity.application.port.SubscriptionRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 到期结算任务：分批扫描、单用户失败隔离、空批退出。
 */
class SubscriptionExpiryJobTest {

    private SubscriptionRepository subscriptionRepository;
    private QuotaRepository quotaRepository;
    private SubscriptionExpirySettlement settlement;
    private SubscriptionExpiryJob job;

    @BeforeEach
    void setUp() {
        subscriptionRepository = mock(SubscriptionRepository.class);
        quotaRepository = mock(QuotaRepository.class);
        settlement = mock(SubscriptionExpirySettlement.class);
        job = new SubscriptionExpiryJob(subscriptionRepository, quotaRepository, settlement);
    }

    @Test
    void expiresOverdueSubscriptionsThenRenewsQuotas() {
        when(subscriptionRepository.findExpiredActiveUserIds(any(), anyInt()))
                .thenReturn(List.of(1L, 2L))
                .thenReturn(List.of());
        when(settlement.expireSubscription(anyLong())).thenReturn(true);
        when(quotaRepository.findExpiredPeriodUserIds(any(LocalDate.class), anyInt()))
                .thenReturn(List.of(3L))
                .thenReturn(List.of());
        when(settlement.renewQuota(anyLong())).thenReturn(true);

        job.run();

        verify(settlement).expireSubscription(1L);
        verify(settlement).expireSubscription(2L);
        verify(settlement).renewQuota(3L);
    }

    @Test
    void singleUserFailureDoesNotAbortBatch() {
        when(subscriptionRepository.findExpiredActiveUserIds(any(), anyInt()))
                .thenReturn(List.of(1L, 2L))
                .thenReturn(List.of());
        when(settlement.expireSubscription(1L)).thenThrow(new IllegalStateException("db blip"));
        when(settlement.expireSubscription(2L)).thenReturn(true);
        when(quotaRepository.findExpiredPeriodUserIds(any(LocalDate.class), anyInt()))
                .thenReturn(List.of());

        job.run();

        // 1 号失败被吞掉，2 号仍然处理；额度批次为空，不触发续发
        verify(settlement).expireSubscription(2L);
        verify(settlement, never()).renewQuota(anyLong());
    }

    @Test
    void stopsWhenBatchMakesNoProgress() {
        // 整批都返回 false（已被并发处理）时应立即退出，避免无限空转。
        when(subscriptionRepository.findExpiredActiveUserIds(any(), anyInt()))
                .thenReturn(List.of(1L, 2L))
                .thenReturn(List.of(1L, 2L));
        when(settlement.expireSubscription(anyLong())).thenReturn(false);
        when(quotaRepository.findExpiredPeriodUserIds(any(LocalDate.class), anyInt()))
                .thenReturn(List.of());

        job.run();

        verify(subscriptionRepository).findExpiredActiveUserIds(any(), anyInt());
    }

    @Test
    void settlementExpireDelegatesToRepository() {
        SubscriptionRepository repo = mock(SubscriptionRepository.class);
        when(repo.expireOverdue(eq(7L), any())).thenReturn(true);
        SubscriptionExpirySettlement real = new SubscriptionExpirySettlement(repo, mock(
                com.offercoach.identity.application.quota.QuotaService.class));

        assertThat(real.expireSubscription(7L)).isTrue();
        verify(repo, never()).expireActive(anyLong());
    }
}
