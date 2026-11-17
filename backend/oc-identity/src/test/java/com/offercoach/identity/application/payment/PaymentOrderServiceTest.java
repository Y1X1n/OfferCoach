package com.offercoach.identity.application.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.offercoach.common.error.BizException;
import com.offercoach.identity.api.dto.OrderResponse;
import com.offercoach.identity.application.port.QuotaRepository;
import com.offercoach.pay.api.PayDtos;
import com.offercoach.pay.client.PayClientException;
import com.offercoach.pay.client.PayGateway;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 取消订单用例：仅 CREATED 委托渠道关单，终态幂等返回，渠道失败不吞异常。
 */
class PaymentOrderServiceTest {

    private QuotaRepository quotaRepository;
    private PayGateway payGateway;
    private PaymentOrderService service;

    @BeforeEach
    void setUp() {
        quotaRepository = mock(QuotaRepository.class);
        payGateway = mock(PayGateway.class);
        service = new PaymentOrderService(quotaRepository, payGateway);
    }

    @Test
    void cancelClosesCreatedOrder() {
        when(payGateway.getOrder("OC-TEST", 7L)).thenReturn(orderView("CREATED"));
        when(payGateway.closeOrder("OC-TEST", 7L)).thenReturn(orderView("CLOSED"));

        OrderResponse response = service.cancel(7L, "OC-TEST");

        assertThat(response.status()).isEqualTo("CLOSED");
        verify(payGateway).closeOrder("OC-TEST", 7L);
    }

    @Test
    void cancelReturnsTerminalOrderWithoutClosing() {
        when(payGateway.getOrder("OC-TEST", 7L)).thenReturn(orderView("PAID"));

        OrderResponse response = service.cancel(7L, "OC-TEST");

        assertThat(response.status()).isEqualTo("PAID");
        verify(payGateway, never()).closeOrder("OC-TEST", 7L);
    }

    @Test
    void cancelPropagatesChannelCloseFailure() {
        when(payGateway.getOrder("OC-TEST", 7L)).thenReturn(orderView("CREATED"));
        when(payGateway.closeOrder("OC-TEST", 7L)).thenThrow(new PayClientException("渠道关单失败", 502));

        assertThatThrownBy(() -> service.cancel(7L, "OC-TEST"))
                .isInstanceOf(BizException.class)
                .satisfies(exception ->
                        assertThat(((BizException) exception).details()).containsEntry("reason", "渠道关单失败"));
    }

    private static PayDtos.OrderView orderView(String status) {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        return new PayDtos.OrderView(
                1L,
                "OC-TEST",
                7L,
                "PRO",
                "专业版",
                3900,
                "CNY",
                "wechat",
                "NATIVE",
                status,
                "weixin://qr",
                now.plusSeconds(1800),
                null,
                0,
                now,
                now);
    }
}
