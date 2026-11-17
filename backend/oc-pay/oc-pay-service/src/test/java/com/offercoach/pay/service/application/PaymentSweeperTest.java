package com.offercoach.pay.service.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.offercoach.pay.service.application.port.PaymentOrderRepository;
import com.offercoach.pay.service.channel.ChannelRegistry;
import com.offercoach.pay.service.channel.PaymentChannel;
import com.offercoach.pay.service.config.PayOpsProperties;
import com.offercoach.pay.service.domain.ChannelCode;
import com.offercoach.pay.service.domain.OrderStatus;
import com.offercoach.pay.service.domain.PaymentExceptionKind;
import com.offercoach.pay.service.domain.PaymentOrder;
import com.offercoach.pay.service.error.PayErrorKind;
import com.offercoach.pay.service.error.PayException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 关单竞态：只有「渠道确认未支付 + 关单成功」才能把订单置为 CLOSED。
 *
 * <p>渠道不可用、查单失败、金额不符、关单未确认时都必须保留待支付，超过宽限期才置 FAILED 并入队。</p>
 */
class PaymentSweeperTest {

    private PaymentOrderRepository orderRepository;
    private ChannelRegistry registry;
    private EntitlementNotifier notifier;
    private PaymentExceptionRecorder exceptions;
    private PaymentChannel channel;
    private PaymentSweeper sweeper;

    @BeforeEach
    void setUp() {
        orderRepository = mock(PaymentOrderRepository.class);
        registry = mock(ChannelRegistry.class);
        notifier = mock(EntitlementNotifier.class);
        exceptions = mock(PaymentExceptionRecorder.class);
        channel = mock(PaymentChannel.class);
        when(registry.find(ChannelCode.WECHAT)).thenReturn(channel);
        when(channel.available()).thenReturn(true);
        sweeper = new PaymentSweeper(orderRepository, registry, notifier, exceptions, new PayOpsProperties());
    }

    @Test
    void closesOnlyWhenChannelConfirmsUnpaidAndCloseSucceeds() {
        PaymentOrder order = order(Instant.now().minus(1, ChronoUnit.MINUTES));
        when(orderRepository.findExpired(100)).thenReturn(List.of(order));
        when(channel.query(order)).thenReturn(new PaymentChannel.ChannelOrderState(false, null, null, "NOTPAY", null));
        when(channel.close(order)).thenReturn(true);

        sweeper.sweep();

        verify(orderRepository).markClosed("OC-TEST");
        verify(orderRepository, never()).markFailed(anyString());
    }

    @Test
    void keepsOrderWhenCloseNotConfirmed() {
        PaymentOrder order = order(Instant.now().minus(1, ChronoUnit.MINUTES));
        when(orderRepository.findExpired(100)).thenReturn(List.of(order));
        when(channel.query(order)).thenReturn(new PaymentChannel.ChannelOrderState(false, null, null, "NOTPAY", null));
        when(channel.close(order)).thenReturn(false);

        sweeper.sweep();

        verify(orderRepository, never()).markClosed(anyString());
        verify(orderRepository, never()).markFailed(anyString());
        verify(exceptions, never()).record(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void keepsOrderWhenChannelQueryFails() {
        PaymentOrder order = order(Instant.now().minus(1, ChronoUnit.MINUTES));
        when(orderRepository.findExpired(100)).thenReturn(List.of(order));
        when(channel.query(order)).thenThrow(new PayException(PayErrorKind.UPSTREAM_FAILED, "超时"));

        sweeper.sweep();

        verify(channel, never()).close(any());
        verify(orderRepository, never()).markClosed(anyString());
    }

    @Test
    void marksFailedOnlyAfterGraceWhenCloseStillUnconfirmed() {
        PaymentOrder order = order(Instant.now().minus(25, ChronoUnit.HOURS));
        when(orderRepository.findExpired(100)).thenReturn(List.of(order));
        when(channel.query(order)).thenReturn(new PaymentChannel.ChannelOrderState(false, null, null, "NOTPAY", null));
        when(channel.close(order)).thenReturn(false);
        when(orderRepository.markFailed("OC-TEST")).thenReturn(true);

        sweeper.sweep();

        verify(orderRepository).markFailed("OC-TEST");
        verify(exceptions)
                .record(
                        eq("OC-TEST"),
                        eq(1L),
                        eq(PaymentExceptionKind.CLOSE_UNCERTAIN),
                        anyString(),
                        eq(3900),
                        eq(ChannelCode.WECHAT),
                        any());
    }

    @Test
    void marksPaidWhenReconcileFindsPaidOrder() {
        PaymentOrder order = order(Instant.now().minus(1, ChronoUnit.MINUTES));
        when(orderRepository.findExpired(100)).thenReturn(List.of(order));
        when(channel.query(order))
                .thenReturn(new PaymentChannel.ChannelOrderState(true, "WX-1", Instant.now(), "SUCCESS", 3900));
        when(orderRepository.markPaid(eq("OC-TEST"), eq("WX-1"), any())).thenReturn(true);
        when(orderRepository.findByOrderNo("OC-TEST")).thenReturn(java.util.Optional.of(order));

        sweeper.sweep();

        verify(notifier).notify(order);
        verify(orderRepository, never()).markClosed(anyString());
    }

    @Test
    void refusesToCloseWhenChannelAmountDiffers() {
        PaymentOrder order = order(Instant.now().minus(1, ChronoUnit.MINUTES));
        when(orderRepository.findExpired(100)).thenReturn(List.of(order));
        when(channel.query(order))
                .thenReturn(new PaymentChannel.ChannelOrderState(true, "WX-1", Instant.now(), "SUCCESS", 1));

        sweeper.sweep();

        verify(orderRepository, never()).markPaid(anyString(), anyString(), any());
        verify(channel, never()).close(any());
        verify(exceptions)
                .record(
                        eq("OC-TEST"),
                        eq(1L),
                        eq(PaymentExceptionKind.AMOUNT_MISMATCH),
                        anyString(),
                        eq(1),
                        eq(ChannelCode.WECHAT),
                        eq("WX-1"));
    }

    private static PaymentOrder order(Instant expiresAt) {
        Instant now = Instant.now();
        return new PaymentOrder(
                1L,
                "OC-TEST",
                1L,
                "PRO",
                "专业版",
                3900,
                "CNY",
                ChannelCode.WECHAT,
                OrderStatus.CREATED,
                null,
                null,
                "weixin://qr",
                "key-1",
                "PENDING",
                0,
                expiresAt,
                null,
                null,
                0,
                now,
                now);
    }
}
