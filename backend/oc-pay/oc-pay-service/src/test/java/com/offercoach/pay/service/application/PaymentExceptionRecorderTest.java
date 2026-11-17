package com.offercoach.pay.service.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.offercoach.pay.service.domain.ChannelCode;
import com.offercoach.pay.service.domain.PaymentExceptionKind;
import com.offercoach.pay.service.infrastructure.persistence.PaymentExceptionMapper;
import org.junit.jupiter.api.Test;

/**
 * 异常入队：参数映射正确，且写库失败不能影响支付主流程。
 */
class PaymentExceptionRecorderTest {

    private final PaymentExceptionMapper mapper = mock(PaymentExceptionMapper.class);
    private final PaymentExceptionRecorder recorder = new PaymentExceptionRecorder(mapper);

    @Test
    void recordsNormalizedFields() {
        recorder.record(
                "OC-1", 2L, PaymentExceptionKind.AMOUNT_MISMATCH, "回调金额=1 订单金额=3900", 1, ChannelCode.WECHAT, "WX-1");

        verify(mapper)
                .insertIfAbsent(
                        eq("OC-1"), eq(2L), eq("AMOUNT_MISMATCH"), anyString(), eq(1), eq("WECHAT"), eq("WX-1"));
    }

    @Test
    void swallowsDatabaseFailure() {
        doThrow(new IllegalStateException("db down"))
                .when(mapper)
                .insertIfAbsent(anyString(), any(), anyString(), anyString(), anyInt(), anyString(), anyString());

        // 不得向上抛：回调应答与订单状态机不能因为异常表写失败而中断
        recorder.record("OC-1", 1L, PaymentExceptionKind.UNKNOWN_ORDER, "detail", null, null, null);

        verify(mapper)
                .insertIfAbsent(eq("OC-1"), eq(1L), eq("UNKNOWN_ORDER"), anyString(), isNull(), isNull(), isNull());
    }
}
