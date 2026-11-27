package com.offercoach.pay.service.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import com.offercoach.pay.api.PayDtos;
import com.offercoach.pay.service.application.port.PaymentOrderRepository;
import com.offercoach.pay.service.application.port.PaymentRefundRepository;
import com.offercoach.pay.service.channel.ChannelRegistry;
import com.offercoach.pay.service.channel.MiniAppPaymentChannel;
import com.offercoach.pay.service.channel.PaymentChannel;
import com.offercoach.pay.service.config.PayOpsProperties;
import com.offercoach.pay.service.domain.ChannelCode;
import com.offercoach.pay.service.domain.OrderStatus;
import com.offercoach.pay.service.domain.PaymentOrder;
import com.offercoach.pay.service.domain.PaymentRefund;
import com.offercoach.pay.service.domain.RefundStatus;
import com.offercoach.pay.service.error.PayException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OrderServiceTest {

    private PaymentOrderRepository orderRepository;
    private PaymentRefundRepository refundRepository;
    private ChannelRegistry registry;
    private PaymentChannel wechat;
    private OrderService service;
    private PaymentReservationService reservationService;

    @BeforeEach
    void setUp() {
        orderRepository = mock(PaymentOrderRepository.class);
        refundRepository = mock(PaymentRefundRepository.class);
        registry = mock(ChannelRegistry.class);
        wechat = mock(PaymentChannel.class);
        PayOpsProperties ops = new PayOpsProperties();
        EntitlementNotifier notifier = mock(EntitlementNotifier.class);
        reservationService = new PaymentReservationService(orderRepository, refundRepository);
        service = new OrderService(orderRepository, refundRepository, registry, reservationService, notifier, ops);
    }

    @Test
    void createOrderPrepaysAndPersists() {
        when(orderRepository.findByUserIdempotency(1L, "key-1")).thenReturn(Optional.empty());
        when(registry.require(ChannelCode.WECHAT)).thenReturn(wechat);
        when(wechat.available()).thenReturn(true);
        when(wechat.prepay(any(), any())).thenReturn(new PaymentChannel.Prepay("weixin://qr", "prepay-1"));
        when(orderRepository.findByOrderNo(anyString())).thenReturn(Optional.empty());

        PayDtos.OrderView view = service.createOrder(
                new PayDtos.CreateOrderRequest(1L, "PRO", "专业版", 3900, "CNY", "wechat", "key-1", "127.0.0.1"));

        assertThat(view.status()).isEqualTo("CREATED");
        assertThat(view.amountCents()).isEqualTo(3900);
        assertThat(view.channel()).isEqualTo("wechat");
        verify(orderRepository).insert(any());
        verify(orderRepository).attachPrepay(anyString(), eq("weixin://qr"), eq("prepay-1"));
    }

    @Test
    void createOrderIsIdempotentByKey() {
        PaymentOrder existing = order(OrderStatus.CREATED, 0, null);
        when(orderRepository.findByUserIdempotency(1L, "key-1")).thenReturn(Optional.of(existing));

        PayDtos.OrderView view = service.createOrder(
                new PayDtos.CreateOrderRequest(1L, "PRO", "专业版", 3900, "CNY", "wechat", "key-1", null));

        assertThat(view.orderNo()).isEqualTo("OC-TEST");
        verify(orderRepository, never()).insert(any());
    }

    @Test
    void createOrderRejectsUnknownChannel() {
        assertThatThrownBy(() -> service.createOrder(
                        new PayDtos.CreateOrderRequest(1L, "PRO", "专业版", 3900, "CNY", "paypal", "key-1", null)))
                .isInstanceOf(PayException.class);
    }

    @Test
    void createOrderRejectsChannelNotAvailable() {
        when(orderRepository.findByUserIdempotency(1L, "key-2")).thenReturn(Optional.empty());
        when(registry.require(ChannelCode.ALIPAY)).thenReturn(wechat);
        when(wechat.available()).thenReturn(false);

        assertThatThrownBy(() -> service.createOrder(
                        new PayDtos.CreateOrderRequest(1L, "PRO", "专业版", 3900, "CNY", "alipay", "key-2", null)))
                .isInstanceOf(PayException.class);
        verify(orderRepository, never()).insert(any());
    }

    @Test
    void refundRejectsAmountBeyondRemaining() {
        PaymentOrder paid = order(OrderStatus.PAID, 1000, "WX-1");
        when(orderRepository.lockByOrderNo("OC-TEST")).thenReturn(Optional.of(paid));
        when(refundRepository.findByOrderIdempotency("OC-TEST", "r1")).thenReturn(Optional.empty());
        when(refundRepository.insertIfAbsent(any())).thenReturn(true);
        // 原子预占失败：剩余可退 2900，申请 3000。
        when(orderRepository.reserveRefundAmount("OC-TEST", 3000)).thenReturn(false);

        assertThatThrownBy(() ->
                        reservationService.reserveRefund(1L, "OC-TEST", new PayDtos.RefundRequest(3000, "重复购买", "r1")))
                .isInstanceOf(PayException.class);
        verify(orderRepository).reserveRefundAmount("OC-TEST", 3000);
    }

    @Test
    void refundReservesWithinRemaining() {
        PaymentOrder paid = order(OrderStatus.PAID, 1000, "WX-1");
        when(orderRepository.lockByOrderNo("OC-TEST")).thenReturn(Optional.of(paid));
        when(refundRepository.findByOrderIdempotency("OC-TEST", "r2")).thenReturn(Optional.empty());
        when(refundRepository.insertIfAbsent(any())).thenReturn(true);
        when(orderRepository.reserveRefundAmount("OC-TEST", 2000)).thenReturn(true);

        PaymentReservationService.Reservation reservation =
                reservationService.reserveRefund(1L, "OC-TEST", new PayDtos.RefundRequest(2000, "部分退款", "r2"));

        assertThat(reservation.created()).isTrue();
        verify(refundRepository).insertIfAbsent(any());
        verify(orderRepository).reserveRefundAmount("OC-TEST", 2000);
    }

    @Test
    void refundReturnsExistingWhenIdempotencyKeyRaces() {
        PaymentOrder paid = order(OrderStatus.PAID, 1000, "WX-1");
        PaymentRefund existing = new PaymentRefund(
                7L,
                "RF-EXIST",
                "OC-TEST",
                1L,
                2000,
                RefundStatus.CREATED,
                null,
                "部分退款",
                "r3",
                Instant.now(),
                Instant.now());
        when(orderRepository.lockByOrderNo("OC-TEST")).thenReturn(Optional.of(paid));
        when(refundRepository.findByOrderIdempotency("OC-TEST", "r3"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));
        // 并发请求已先插入同一幂等键：insertIfAbsent 返回 false 而不是抛异常。
        when(refundRepository.insertIfAbsent(any())).thenReturn(false);

        PaymentReservationService.Reservation reservation =
                reservationService.reserveRefund(1L, "OC-TEST", new PayDtos.RefundRequest(2000, "部分退款", "r3"));

        assertThat(reservation.created()).isFalse();
        assertThat(reservation.refund().refundNo()).isEqualTo("RF-EXIST");
        verify(orderRepository, never()).reserveRefundAmount(anyString(), anyInt());
    }

    @Test
    void closeOrderRefusesLocalCloseWhenChannelNotConfirmed() {
        when(orderRepository.findByOrderNo("OC-TEST")).thenReturn(Optional.of(order(OrderStatus.CREATED, 0, null)));
        when(registry.find(ChannelCode.WECHAT)).thenReturn(wechat);
        when(wechat.close(any())).thenReturn(false);

        assertThatThrownBy(() -> service.closeOrder(1L, "OC-TEST")).isInstanceOf(PayException.class);
        verify(orderRepository, never()).markClosed(anyString());
    }

    @Test
    void closeOrderMarksClosedWhenChannelConfirmed() {
        when(orderRepository.findByOrderNo("OC-TEST"))
                .thenReturn(Optional.of(order(OrderStatus.CREATED, 0, null)))
                .thenReturn(Optional.of(order(OrderStatus.CLOSED, 0, null)));
        when(registry.find(ChannelCode.WECHAT)).thenReturn(wechat);
        when(wechat.close(any())).thenReturn(true);

        PayDtos.OrderView view = service.closeOrder(1L, "OC-TEST");

        assertThat(view.status()).isEqualTo("CLOSED");
        verify(orderRepository).markClosed("OC-TEST");
    }

    @Test
    void createMiniOrderSkipsQrPrepay() {
        when(orderRepository.findByUserIdempotency(1L, "key-mini")).thenReturn(Optional.empty());
        PaymentChannel alipay = miniChannel();
        when(registry.require(ChannelCode.ALIPAY)).thenReturn(alipay);
        when(alipay.available()).thenReturn(true);
        when(orderRepository.findByOrderNo(anyString())).thenReturn(Optional.empty());

        PayDtos.OrderView view = service.createOrder(new PayDtos.CreateOrderRequest(
                1L, "PRO", "专业版", 3900, "CNY", "alipay", "key-mini", "127.0.0.1", "MINI"));

        assertThat(view.status()).isEqualTo("CREATED");
        verify(alipay, never()).prepay(any(), any());
        verify(orderRepository, never()).attachPrepay(anyString(), any(), any());
    }

    @Test
    void miniPrepayReturnsTradeNo() {
        when(orderRepository.findByOrderNo("OC-TEST")).thenReturn(Optional.of(miniOrder(null)));
        PaymentChannel alipay = miniChannel();
        when(registry.require(ChannelCode.ALIPAY)).thenReturn(alipay);
        when(alipay.available()).thenReturn(true);
        when(((MiniAppPaymentChannel) alipay).miniPrepay(any(), anyString())).thenReturn("2026111700001");

        PayDtos.MiniPayView view = service.miniPrepay(1L, "OC-TEST", "auth-code");

        assertThat(view.tradeNo()).isEqualTo("2026111700001");
        assertThat(view.status()).isEqualTo("CREATED");
        verify(orderRepository).attachPrepay(eq("OC-TEST"), isNull(), eq("2026111700001"));
    }

    @Test
    void miniPrepayRejectsQrOrder() {
        when(orderRepository.findByOrderNo("OC-TEST")).thenReturn(Optional.of(miniOrder("https://qr.alipay.com/x")));

        assertThatThrownBy(() -> service.miniPrepay(1L, "OC-TEST", "auth-code"))
                .isInstanceOf(PayException.class)
                .hasMessageContaining("扫码");
    }

    @Test
    void miniPrepayRejectsPaidOrder() {
        PaymentOrder paid = order(OrderStatus.PAID, 0, "txn-1");
        when(orderRepository.findByOrderNo("OC-TEST")).thenReturn(Optional.of(paid));

        assertThatThrownBy(() -> service.miniPrepay(1L, "OC-TEST", "auth-code"))
                .isInstanceOf(PayException.class)
                .hasMessageContaining("状态");
    }

    private static PaymentChannel miniChannel() {
        return mock(PaymentChannel.class, withSettings().extraInterfaces(MiniAppPaymentChannel.class));
    }

    private static PaymentOrder miniOrder(String codeUrl) {
        Instant now = Instant.now();
        return new PaymentOrder(
                1L,
                "OC-TEST",
                1L,
                "PRO",
                "专业版",
                3900,
                "CNY",
                ChannelCode.ALIPAY,
                OrderStatus.CREATED,
                null,
                null,
                codeUrl,
                "key-1",
                "PENDING",
                0,
                now.plus(30, ChronoUnit.MINUTES),
                null,
                null,
                0,
                now,
                now);
    }

    private static PaymentOrder order(OrderStatus status, int refundedCents, String providerTxnId) {
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
                providerTxnId,
                null,
                "weixin://qr",
                "key-1",
                "NOTIFIED",
                1,
                now.plus(30, ChronoUnit.MINUTES),
                status == OrderStatus.PAID ? now : null,
                null,
                refundedCents,
                now,
                now);
    }
}
