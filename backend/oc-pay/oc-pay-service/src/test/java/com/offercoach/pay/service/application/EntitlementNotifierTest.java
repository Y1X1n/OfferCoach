package com.offercoach.pay.service.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offercoach.pay.config.PayProperties;
import com.offercoach.pay.service.application.port.PaymentOrderRepository;
import com.offercoach.pay.service.config.PayOpsProperties;
import com.offercoach.pay.service.domain.ChannelCode;
import com.offercoach.pay.service.domain.OrderStatus;
import com.offercoach.pay.service.domain.PaymentExceptionKind;
import com.offercoach.pay.service.domain.PaymentOrder;
import com.offercoach.pay.service.support.SimpleHttp;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 发放失败闭环：4xx 永久拒绝直接死信；5xx 重试到上限后死信并写异常队列，不再静默放弃。
 */
class EntitlementNotifierTest {

    private PaymentOrderRepository orderRepository;
    private PaymentExceptionRecorder exceptions;
    private SimpleHttp http;
    private PayOpsProperties ops;
    private EntitlementNotifier notifier;

    @BeforeEach
    void setUp() {
        orderRepository = mock(PaymentOrderRepository.class);
        exceptions = mock(PaymentExceptionRecorder.class);
        http = mock(SimpleHttp.class);
        ops = new PayOpsProperties();
        ops.setNotifyMaxAttempts(8);
        notifier = new EntitlementNotifier(
                ops,
                new PayProperties(),
                orderRepository,
                exceptions,
                new ObjectMapper().findAndRegisterModules(),
                http);
    }

    @Test
    void marksNotifiedOnSuccess() throws Exception {
        HttpResponse<String> success = response(200);
        when(http.send(anyString(), anyString(), anyMap(), anyString(), any(Duration.class)))
                .thenReturn(success);

        notifier.notify(order(0));

        verify(orderRepository).updateNotify("OC-TEST", "NOTIFIED", 8);
        verify(exceptions, never()).record(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void deadLettersPermanentRejection() throws Exception {
        HttpResponse<String> rejected = response(400);
        when(http.send(anyString(), anyString(), anyMap(), anyString(), any(Duration.class)))
                .thenReturn(rejected);

        notifier.notify(order(1));

        verify(orderRepository).updateNotify("OC-TEST", "DEAD", 8);
        verify(exceptions)
                .record(
                        eq("OC-TEST"),
                        eq(1L),
                        eq(PaymentExceptionKind.ENTITLEMENT_REJECTED),
                        anyString(),
                        eq(3900),
                        eq(ChannelCode.WECHAT),
                        eq("WX-1"));
    }

    @Test
    void keepsRetryingBeforeMaxAttempts() throws Exception {
        HttpResponse<String> failed = response(500);
        when(http.send(anyString(), anyString(), anyMap(), anyString(), any(Duration.class)))
                .thenReturn(failed);

        notifier.notify(order(1));

        verify(orderRepository).updateNotify("OC-TEST", "FAILED", 8);
        verify(exceptions, never()).record(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void deadLettersWhenAttemptsExhausted() throws Exception {
        HttpResponse<String> failed = response(500);
        when(http.send(anyString(), anyString(), anyMap(), anyString(), any(Duration.class)))
                .thenReturn(failed);

        notifier.notify(order(7));

        verify(orderRepository).updateNotify("OC-TEST", "DEAD", 8);
        verify(exceptions)
                .record(
                        eq("OC-TEST"),
                        eq(1L),
                        eq(PaymentExceptionKind.ENTITLEMENT_DEAD),
                        anyString(),
                        eq(3900),
                        eq(ChannelCode.WECHAT),
                        eq("WX-1"));
    }

    @Test
    void skipsDeadOrder() {
        notifier.notify(order(8, "DEAD"));

        verify(orderRepository, never()).updateNotify(anyString(), anyString(), anyInt());
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> response(int status) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn("{}");
        return response;
    }

    private static PaymentOrder order(int notifyAttempts) {
        return order(notifyAttempts, "FAILED");
    }

    private static PaymentOrder order(int notifyAttempts, String notifyStatus) {
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
                OrderStatus.PAID,
                "WX-1",
                null,
                "weixin://qr",
                "key-1",
                notifyStatus,
                notifyAttempts,
                now.plus(30, ChronoUnit.MINUTES),
                now,
                null,
                0,
                now,
                now);
    }
}
