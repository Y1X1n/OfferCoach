package com.offercoach.identity.application.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.offercoach.common.error.BizException;
import com.offercoach.identity.application.port.PaymentEntitlementRepository;
import com.offercoach.identity.application.port.PaymentOrderFactsPort;
import com.offercoach.identity.application.port.QuotaRepository;
import com.offercoach.identity.application.port.SubscriptionRepository;
import com.offercoach.identity.application.quota.QuotaService;
import com.offercoach.pay.api.PayDtos;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 发放前必须回查订单事实：仅凭内部通知内容不能发放套餐。
 */
class PaymentEntitlementServiceTest {

    private PaymentEntitlementRepository entitlementRepository;
    private PaymentOrderFactsPort orderFactsPort;
    private QuotaRepository quotaRepository;
    private PaymentEntitlementService service;

    @BeforeEach
    void setUp() {
        quotaRepository = mock(QuotaRepository.class);
        SubscriptionRepository subscriptionRepository = mock(SubscriptionRepository.class);
        entitlementRepository = mock(PaymentEntitlementRepository.class);
        orderFactsPort = mock(PaymentOrderFactsPort.class);
        QuotaService quotaService = mock(QuotaService.class);
        service = new PaymentEntitlementService(
                quotaRepository, subscriptionRepository, entitlementRepository, orderFactsPort, quotaService);
    }

    @Test
    void rejectsWhenOrderMissing() {
        when(orderFactsPort.find("OC-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.apply(notice(1L, "PRO", 3900))).isInstanceOf(BizException.class);
        verify(entitlementRepository, never()).tryInsert(anyString(), anyLong(), anyString(), anyInt());
    }

    @Test
    void rejectsWhenOrderNotPaid() {
        when(orderFactsPort.find("OC-1")).thenReturn(Optional.of(facts(1L, "PRO", 3900, "CREATED")));

        assertThatThrownBy(() -> service.apply(notice(1L, "PRO", 3900))).isInstanceOf(BizException.class);
        verify(entitlementRepository, never()).tryInsert(anyString(), anyLong(), anyString(), anyInt());
    }

    @Test
    void rejectsWhenUserOrPlanOrAmountDiffers() {
        when(orderFactsPort.find("OC-1")).thenReturn(Optional.of(facts(2L, "PRO", 3900, "PAID")));
        assertThatThrownBy(() -> service.apply(notice(1L, "PRO", 3900))).isInstanceOf(BizException.class);

        when(orderFactsPort.find("OC-1")).thenReturn(Optional.of(facts(1L, "FREE", 3900, "PAID")));
        assertThatThrownBy(() -> service.apply(notice(1L, "PRO", 3900))).isInstanceOf(BizException.class);

        when(orderFactsPort.find("OC-1")).thenReturn(Optional.of(facts(1L, "PRO", 100, "PAID")));
        assertThatThrownBy(() -> service.apply(notice(1L, "PRO", 3900))).isInstanceOf(BizException.class);

        verify(entitlementRepository, never()).tryInsert(anyString(), anyLong(), anyString(), anyInt());
    }

    @Test
    void duplicateNoticeIsIdempotentAndDoesNotGrantTwice() {
        when(orderFactsPort.find("OC-1")).thenReturn(Optional.of(facts(1L, "PRO", 3900, "PAID")));
        when(entitlementRepository.tryInsert("OC-1", 1L, "PRO", 3900)).thenReturn(false);

        PayDtos.EntitlementAck ack = service.apply(notice(1L, "PRO", 3900));

        assertThat(ack.alreadyApplied()).isTrue();
        assertThat(ack.applied()).isFalse();
        verify(quotaRepository, never()).findPlan(anyString());
        verify(quotaRepository, never()).updatePlanPeriod(anyLong(), anyString(), any(), any());
    }

    private static PayDtos.EntitlementNotice notice(long userId, String planCode, int amountCents) {
        return new PayDtos.EntitlementNotice(
                "OC-1", userId, planCode, amountCents, "CNY", "wechat", "WX-1", Instant.now());
    }

    private static PaymentOrderFactsPort.Facts facts(long userId, String planCode, int amountCents, String status) {
        return new PaymentOrderFactsPort.Facts(userId, planCode, amountCents, status);
    }
}
