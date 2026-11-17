package com.offercoach.pay.service.application;

import com.offercoach.pay.service.domain.ChannelCode;
import com.offercoach.pay.service.domain.PaymentExceptionKind;
import com.offercoach.pay.service.infrastructure.persistence.PaymentExceptionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 支付异常入队：写失败只告警，不影响回调应答与订单状态机。
 *
 * <p>异常表是人工处理队列的事实来源；写库失败时必须留下 ERROR 日志，避免问题彻底消失。</p>
 */
@Component
public class PaymentExceptionRecorder {

    private static final Logger log = LoggerFactory.getLogger(PaymentExceptionRecorder.class);
    private static final int MAX_DETAIL = 500;

    private final PaymentExceptionMapper mapper;

    public PaymentExceptionRecorder(PaymentExceptionMapper mapper) {
        this.mapper = mapper;
    }

    public void record(
            String orderNo,
            Long userId,
            PaymentExceptionKind kind,
            String detail,
            Integer amountCents,
            ChannelCode channel,
            String providerTxnId) {
        try {
            mapper.insertIfAbsent(
                    orderNo,
                    userId,
                    kind.code(),
                    truncate(detail),
                    amountCents,
                    channel == null ? null : channel.name(),
                    providerTxnId);
            log.error("支付异常已入队待人工处理：kind={} orderNo={} detail={}", kind.code(), orderNo, detail);
        } catch (RuntimeException exception) {
            log.error(
                    "支付异常入队失败，需立即人工核对：kind={} orderNo={} detail={} reason={}",
                    kind.code(),
                    orderNo,
                    detail,
                    exception.getClass().getSimpleName());
        }
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= MAX_DETAIL ? value : value.substring(0, MAX_DETAIL);
    }
}
