package com.offercoach.pay.service.application;

import com.offercoach.pay.service.application.port.PaymentOrderRepository;
import com.offercoach.pay.service.channel.ChannelRegistry;
import com.offercoach.pay.service.channel.PaymentChannel;
import com.offercoach.pay.service.config.PayOpsProperties;
import com.offercoach.pay.service.domain.OrderStatus;
import com.offercoach.pay.service.domain.PaymentOrder;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 支付兜底扫描：关闭过期订单、对账遗漏回调、重试额度发放通知。
 *
 * <p>回调是主路径；本任务是安全网，保证「渠道已支付但本地未置位」「已支付但额度未发放」最终一致。</p>
 */
@Component
public class PaymentSweeper {

    private static final Logger log = LoggerFactory.getLogger(PaymentSweeper.class);
    private static final int BATCH = 100;
    private static final Duration RECONCILE_COOLDOWN = Duration.ofSeconds(30);

    private final PaymentOrderRepository orderRepository;
    private final ChannelRegistry registry;
    private final EntitlementNotifier notifier;
    private final PayOpsProperties ops;

    public PaymentSweeper(
            PaymentOrderRepository orderRepository,
            ChannelRegistry registry,
            EntitlementNotifier notifier,
            PayOpsProperties ops) {
        this.orderRepository = orderRepository;
        this.registry = registry;
        this.notifier = notifier;
        this.ops = ops;
    }

    @Scheduled(cron = "${oc.pay.ops.sweep-cron:0 */3 * * * *}")
    public void sweep() {
        closeExpiredOrders();
        reconcileMissedPayments();
        retryPendingNotify();
    }

    /**
     * 过期订单：先查渠道，确已支付则补记；否则关闭。
     */
    private void closeExpiredOrders() {
        for (PaymentOrder order : orderRepository.findExpired(BATCH)) {
            try {
                if (reconcile(order)) {
                    continue;
                }
                PaymentChannel channel = registry.find(order.channel());
                if (channel != null) {
                    channel.close(order);
                }
                orderRepository.markClosed(order.orderNo());
            } catch (RuntimeException exception) {
                log.warn("过期订单处理失败：orderNo={} reason={}", order.orderNo(), exception.getMessage());
            }
        }
    }

    /**
     * 回调遗漏对账：仍待支付但已过冷却期的订单主动查询渠道。
     */
    private void reconcileMissedPayments() {
        Instant createdBefore = Instant.now().minus(RECONCILE_COOLDOWN);
        for (PaymentOrder order : orderRepository.findReconcileCandidates(createdBefore, BATCH)) {
            try {
                reconcile(order);
            } catch (RuntimeException exception) {
                log.warn("订单对账失败：orderNo={} reason={}", order.orderNo(), exception.getMessage());
            }
        }
    }

    /**
     * 已支付但额度未发放的订单重试下发。
     */
    private void retryPendingNotify() {
        for (PaymentOrder order : orderRepository.findPendingNotify(ops.getNotifyMaxAttempts(), BATCH)) {
            try {
                notifier.notify(order);
            } catch (RuntimeException exception) {
                log.warn("额度发放重试失败：orderNo={} reason={}", order.orderNo(), exception.getMessage());
            }
        }
    }

    /**
     * 查询渠道并对已支付订单就地补记；返回是否确认为已支付。
     */
    private boolean reconcile(PaymentOrder order) {
        if (order.status() != OrderStatus.CREATED || !order.payable(Instant.now())) {
            return false;
        }
        PaymentChannel channel = registry.find(order.channel());
        if (channel == null || !channel.available()) {
            return false;
        }
        PaymentChannel.ChannelOrderState state = channel.query(order);
        if (!state.paid()) {
            return false;
        }
        boolean transitioned = orderRepository.markPaid(order.orderNo(), state.providerTxnId(), state.paidAt());
        if (transitioned) {
            log.info("订单对账补记已支付：orderNo={}", order.orderNo());
            orderRepository.findByOrderNo(order.orderNo()).ifPresent(notifier::notify);
        }
        return true;
    }
}
