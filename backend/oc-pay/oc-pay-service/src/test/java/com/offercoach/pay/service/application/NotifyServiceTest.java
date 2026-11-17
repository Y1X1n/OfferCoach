package com.offercoach.pay.service.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.offercoach.pay.service.application.port.PaymentOrderRepository;
import com.offercoach.pay.service.channel.ChannelRegistry;
import com.offercoach.pay.service.channel.PaymentChannel;
import com.offercoach.pay.service.domain.ChannelCode;
import com.offercoach.pay.service.domain.OrderStatus;
import com.offercoach.pay.service.domain.PaymentExceptionKind;
import com.offercoach.pay.service.domain.PaymentOrder;
import com.offercoach.pay.service.error.PayException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 回调交叉校验：验签之外还要核对订单存在、渠道一致、金额一致与流水号。
 *
 * <p>重点覆盖「已付款但无法自动发放」的场景：必须落异常表，不允许只写日志。</p>
 */
class NotifyServiceTest {

    private PaymentOrderRepository orderRepository;
    private EntitlementNotifier notifier;
    private PaymentExceptionRecorder exceptions;
    private PaymentChannel channel;
    private PaymentChannel alipay;
    private NotifyService service;

    @BeforeEach
    void setUp() {
        ChannelRegistry registry = mock(ChannelRegistry.class);
        orderRepository = mock(PaymentOrderRepository.class);
        notifier = mock(EntitlementNotifier.class);
        exceptions = mock(PaymentExceptionRecorder.class);
        channel = mock(PaymentChannel.class);
        alipay = mock(PaymentChannel.class);
        when(registry.require(ChannelCode.WECHAT)).thenReturn(channel);
        when(registry.require(ChannelCode.ALIPAY)).thenReturn(alipay);
        when(channel.successResponseBody()).thenReturn("{\"code\":\"SUCCESS\"}");
        when(channel.parseNotify(any(), anyString())).thenReturn(notify(true, 3900));
        when(alipay.parseNotify(any(), anyString())).thenReturn(notify(true, 3900));
        service = new NotifyService(registry, orderRepository, notifier, exceptions);
    }

    @Test
    void marksPaidAndNotifiesOnValidCallback() {
        PaymentOrder created = order(OrderStatus.CREATED);
        PaymentOrder paid = order(OrderStatus.PAID);
        when(orderRepository.findByOrderNo("OC-TEST")).thenReturn(Optional.of(created), Optional.of(paid));
        when(orderRepository.markPaid(eq("OC-TEST"), eq("WX-1"), any())).thenReturn(true);

        String response = service.handle(ChannelCode.WECHAT, Map.of(), "{}");

        assertThat(response).contains("SUCCESS");
        verify(orderRepository).markPaid(eq("OC-TEST"), eq("WX-1"), any());
        verify(notifier).notify(paid);
        verify(orderRepository)
                .recordNotify(eq("OC-TEST"), eq("wechat"), anyString(), eq("WX-1"), eq(true), eq(true), anyString());
        verify(exceptions, never()).record(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsAmountMismatchWithoutChangingState() {
        when(channel.parseNotify(any(), anyString())).thenReturn(notify(true, 1));
        when(orderRepository.findByOrderNo("OC-TEST")).thenReturn(Optional.of(order(OrderStatus.CREATED)));

        assertThatThrownBy(() -> service.handle(ChannelCode.WECHAT, Map.of(), "{}"))
                .isInstanceOf(PayException.class);

        verify(orderRepository, never()).markPaid(anyString(), anyString(), any());
        verify(exceptions)
                .record(
                        eq("OC-TEST"),
                        eq(1L),
                        eq(PaymentExceptionKind.AMOUNT_MISMATCH),
                        anyString(),
                        eq(1),
                        eq(ChannelCode.WECHAT),
                        eq("WX-1"));
        verify(orderRepository)
                .recordNotify(eq("OC-TEST"), eq("wechat"), anyString(), eq("WX-1"), eq(true), eq(false), anyString());
    }

    @Test
    void rejectsUnknownOrderAndRecordsException() {
        when(orderRepository.findByOrderNo("OC-TEST")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.handle(ChannelCode.WECHAT, Map.of(), "{}"))
                .isInstanceOf(PayException.class);

        verify(exceptions)
                .record(
                        eq("OC-TEST"),
                        isNull(),
                        eq(PaymentExceptionKind.UNKNOWN_ORDER),
                        anyString(),
                        eq(3900),
                        eq(ChannelCode.WECHAT),
                        eq("WX-1"));
    }

    @Test
    void rejectsChannelMismatch() {
        when(orderRepository.findByOrderNo("OC-TEST")).thenReturn(Optional.of(order(OrderStatus.CREATED)));

        assertThatThrownBy(() -> service.handle(ChannelCode.ALIPAY, Map.of(), "{}"))
                .isInstanceOf(PayException.class);

        verify(orderRepository, never()).markPaid(anyString(), anyString(), any());
        verify(exceptions)
                .record(
                        anyString(),
                        eq(1L),
                        eq(PaymentExceptionKind.CHANNEL_MISMATCH),
                        anyString(),
                        any(),
                        eq(ChannelCode.ALIPAY),
                        anyString());
    }

    @Test
    void recordsPaidOnInactiveOrderInsteadOfSilentWarn() {
        PaymentOrder closed = order(OrderStatus.CLOSED);
        when(orderRepository.findByOrderNo("OC-TEST")).thenReturn(Optional.of(closed));
        when(orderRepository.markPaid(anyString(), anyString(), any())).thenReturn(false);

        // 资金已发生、重试无法改变结果：仍应答成功，但必须入队。
        service.handle(ChannelCode.WECHAT, Map.of(), "{}");

        verify(exceptions)
                .record(
                        eq("OC-TEST"),
                        eq(1L),
                        eq(PaymentExceptionKind.PAID_ON_INACTIVE_ORDER),
                        anyString(),
                        eq(3900),
                        eq(ChannelCode.WECHAT),
                        eq("WX-1"));
        verify(notifier, never()).notify(any());
    }

    @Test
    void duplicateCallbackOnPaidOrderIsIdempotent() {
        when(orderRepository.findByOrderNo("OC-TEST")).thenReturn(Optional.of(order(OrderStatus.PAID)));
        when(orderRepository.markPaid(anyString(), anyString(), any())).thenReturn(false);

        service.handle(ChannelCode.WECHAT, Map.of(), "{}");

        verify(exceptions, never()).record(any(), any(), any(), any(), any(), any(), any());
        verify(notifier, never()).notify(any());
    }

    @Test
    void rejectsCallbackWithoutProviderTxnId() {
        when(channel.parseNotify(any(), anyString())).thenReturn(notify(true, 3900, ""));
        when(orderRepository.findByOrderNo("OC-TEST")).thenReturn(Optional.of(order(OrderStatus.CREATED)));

        assertThatThrownBy(() -> service.handle(ChannelCode.WECHAT, Map.of(), "{}"))
                .isInstanceOf(PayException.class);
        verify(orderRepository, never()).markPaid(anyString(), anyString(), any());
    }

    private static PaymentChannel.ChannelNotify notify(boolean paid, Integer amountCents) {
        return notify(paid, amountCents, "WX-1");
    }

    private static PaymentChannel.ChannelNotify notify(boolean paid, Integer amountCents, String txnId) {
        return new PaymentChannel.ChannelNotify(
                "OC-TEST", txnId, paid, amountCents, "SUCCESS", "TRANSACTION.SUCCESS", Instant.now());
    }

    private static PaymentOrder order(OrderStatus status) {
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
                status,
                status == OrderStatus.PAID ? "WX-1" : null,
                null,
                "weixin://qr",
                "key-1",
                status == OrderStatus.PAID ? "NOTIFIED" : "PENDING",
                0,
                now.plus(30, ChronoUnit.MINUTES),
                status == OrderStatus.PAID ? now : null,
                status == OrderStatus.CLOSED ? now : null,
                0,
                now,
                now);
    }

    @SuppressWarnings("unused")
    private static ArgumentCaptor<PaymentOrder> orderCaptor() {
        return ArgumentCaptor.forClass(PaymentOrder.class);
    }
}
