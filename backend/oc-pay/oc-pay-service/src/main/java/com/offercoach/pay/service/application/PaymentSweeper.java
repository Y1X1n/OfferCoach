package com.offercoach.pay.service.application;

import com.offercoach.pay.service.application.port.PaymentOrderRepository;
import com.offercoach.pay.service.channel.ChannelRegistry;
import com.offercoach.pay.service.channel.PaymentChannel;
import com.offercoach.pay.service.config.PayOpsProperties;
import com.offercoach.pay.service.domain.OrderStatus;
import com.offercoach.pay.service.domain.PaymentExceptionKind;
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
 *
 * <p>关单是保守的：只有渠道明确返回未支付、且关单被渠道确认后才把本地订单置为 CLOSED。
 * 渠道不可用或关单未确认时保留待支付状态继续重试，超过宽限期仍无法确认则置 FAILED 并进入人工队列，
 * 避免「用户事后支付成功但本地已关单」造成资金损失。</p>
 */
@Component
public class PaymentSweeper {

    private static final Logger log = LoggerFactory.getLogger(PaymentSweeper.class);
    private static final int BATCH = 100;
    private static final Duration RECONCILE_COOLDOWN = Duration.ofSeconds(30);

    private final PaymentOrderRepository orderRepository;
    private final ChannelRegistry registry;
    private final EntitlementNotifier notifier;
    private final PaymentExceptionRecorder exceptions;
    private final PayOpsProperties ops;

    public PaymentSweeper(
            PaymentOrderRepository orderRepository,
            ChannelRegistry registry,
            EntitlementNotifier notifier,
            PaymentExceptionRecorder exceptions,
            PayOpsProperties ops) {
        this.orderRepository = orderRepository;
        this.registry = registry;
        this.notifier = notifier;
        this.exceptions = exceptions;
        this.ops = ops;
    }

    @Scheduled(cron = "${oc.pay.ops.sweep-cron:0 */3 * * * *}")
    public void sweep() {
        closeExpiredOrders();
        reconcileMissedPayments();
        retryPendingNotify();
    }

    /**
     * 过期订单：先查渠道，确已支付则补记；确认未支付且关单成功才关闭，其余一律保留等待重试。
     */
    private void closeExpiredOrders() {
        for (PaymentOrder order : orderRepository.findExpired(BATCH)) {
            try {
                CloseDecision decision = reconcile(order);
                if (decision == CloseDecision.PAID) {
                    continue;
                }
                if (decision == CloseDecision.UNKNOWN) {
                    handleUncertainClose(order);
                    continue;
                }
                PaymentChannel channel = registry.find(order.channel());
                if (channel == null) {
                    handleUncertainClose(order);
                    continue;
                }
                if (channel.close(order)) {
                    orderRepository.markClosed(order.orderNo());
                } else {
                    handleUncertainClose(order);
                }
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
     * 查询渠道并对已支付订单就地补记；返回调用方是否可以安全关闭本地订单。
     *
     * <p>不限制订单是否过期：过期订单在关闭前先对账，避免误关已支付订单。</p>
     */
    private CloseDecision reconcile(PaymentOrder order) {
        if (order.status() != OrderStatus.CREATED) {
            return CloseDecision.PAID;
        }
        PaymentChannel channel = registry.find(order.channel());
        if (channel == null || !channel.available()) {
            return CloseDecision.UNKNOWN;
        }
        PaymentChannel.ChannelOrderState state;
        try {
            state = channel.query(order);
        } catch (RuntimeException exception) {
            log.warn("渠道查单失败，保留待支付等待下轮：orderNo={} reason={}", order.orderNo(), exception.getMessage());
            return CloseDecision.UNKNOWN;
        }
        if (!state.paid()) {
            return CloseDecision.UNPAID;
        }
        // 金额必须可与订单比对：渠道没返回金额时不能默认"一致"。
        // 否则一条只带 trade_state=SUCCESS 的残缺响应（或伪造响应）就能把订单置为已支付并发额度。
        // 回调路径（NotifyService）是同样的要求，两条路径不能一个严一个松。
        if (state.amountCents() == null) {
            exceptions.record(
                    order.orderNo(),
                    order.userId(),
                    PaymentExceptionKind.PAID_UNVERIFIED,
                    "渠道已支付但未返回金额，无法核对",
                    null,
                    order.channel(),
                    state.providerTxnId());
            return CloseDecision.UNKNOWN;
        }
        if (state.amountCents() != order.amountCents()) {
            exceptions.record(
                    order.orderNo(),
                    order.userId(),
                    PaymentExceptionKind.AMOUNT_MISMATCH,
                    "渠道已支付但金额不符：渠道=" + state.amountCents() + " 订单=" + order.amountCents(),
                    state.amountCents(),
                    order.channel(),
                    state.providerTxnId());
            return CloseDecision.UNKNOWN;
        }
        // 渠道流水号是"这笔钱确实到账"的唯一外部凭据，缺失时同样不补记。
        if (state.providerTxnId() == null || state.providerTxnId().isBlank()) {
            exceptions.record(
                    order.orderNo(),
                    order.userId(),
                    PaymentExceptionKind.PAID_UNVERIFIED,
                    "渠道已支付但未返回流水号，无法核对",
                    state.amountCents(),
                    order.channel(),
                    null);
            return CloseDecision.UNKNOWN;
        }
        Instant paidAt = state.paidAt() == null ? Instant.now() : state.paidAt();
        boolean transitioned = orderRepository.markPaid(order.orderNo(), state.providerTxnId(), paidAt);
        if (transitioned) {
            log.info("订单对账补记已支付：orderNo={}", order.orderNo());
            orderRepository.findByOrderNo(order.orderNo()).ifPresent(notifier::notify);
        }
        return CloseDecision.PAID;
    }

    /**
     * 无法确认关单：宽限期内保留待支付继续重试，超过宽限期置 FAILED 并进入人工队列。
     */
    private void handleUncertainClose(PaymentOrder order) {
        Instant graceEnd =
                order.expiresAt() == null ? Instant.now() : order.expiresAt().plus(ops.getCloseGrace());
        if (Instant.now().isBefore(graceEnd)) {
            log.warn("渠道不可用或关单未确认，订单保留待支付等待下轮：orderNo={} channel={}", order.orderNo(), order.channel());
            return;
        }
        if (orderRepository.markFailed(order.orderNo())) {
            exceptions.record(
                    order.orderNo(),
                    order.userId(),
                    PaymentExceptionKind.CLOSE_UNCERTAIN,
                    "订单过期且超过宽限期仍无法确认渠道关单，已置 FAILED",
                    order.amountCents(),
                    order.channel(),
                    order.providerTxnId());
        }
    }

    /**
     * 对账结论：只有在 CONFIRM_UNPAID 时调用方才可以关闭本地订单。
     */
    private enum CloseDecision {
        /** 已支付（含本次补记），无需关闭。 */
        PAID,
        /** 渠道明确返回未支付，可以关单。 */
        UNPAID,
        /** 渠道不可用、查单失败或金额不符，禁止关闭。 */
        UNKNOWN
    }
}
