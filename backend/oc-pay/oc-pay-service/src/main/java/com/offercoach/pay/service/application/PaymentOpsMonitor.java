package com.offercoach.pay.service.application;

import com.offercoach.pay.service.config.PayOpsProperties;
import com.offercoach.pay.service.infrastructure.persistence.PaymentExceptionDO;
import com.offercoach.pay.service.infrastructure.persistence.PaymentExceptionMapper;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 支付异常队列巡检：存在 OPEN 异常时输出 ERROR 日志，作为告警接入点。
 *
 * <p>异常本身的处理（退款、补发、忽略）是人工动作；这里只保证「不会没人知道」。</p>
 */
@Component
public class PaymentOpsMonitor {

    private static final Logger log = LoggerFactory.getLogger(PaymentOpsMonitor.class);
    private static final int SAMPLE = 5;

    private final PaymentExceptionMapper exceptionMapper;
    private final PayOpsProperties ops;

    public PaymentOpsMonitor(PaymentExceptionMapper exceptionMapper, PayOpsProperties ops) {
        this.exceptionMapper = exceptionMapper;
        this.ops = ops;
    }

    @Scheduled(cron = "${oc.pay.ops.alert-cron:0 */30 * * * *}")
    public void report() {
        int open;
        try {
            open = exceptionMapper.countOpen();
        } catch (RuntimeException exception) {
            log.warn("支付异常队列巡检失败：{}", exception.getClass().getSimpleName());
            return;
        }
        if (open == 0) {
            return;
        }
        List<PaymentExceptionDO> samples = exceptionMapper.findOpen(SAMPLE);
        String orders = samples.stream()
                .map(item -> item.getKind() + "/" + item.getOrderNo())
                .reduce((left, right) -> left + ", " + right)
                .orElse("-");
        log.error(
                "存在 {} 条待人工处理的支付异常（payment_exception.status=OPEN），样例：{}；发放重试上限配置={}",
                open,
                orders,
                ops.getNotifyMaxAttempts());
    }
}
