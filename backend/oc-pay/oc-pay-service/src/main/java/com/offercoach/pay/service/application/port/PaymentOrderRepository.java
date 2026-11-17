package com.offercoach.pay.service.application.port;

import com.offercoach.pay.service.domain.PaymentOrder;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 订单仓储端口；事务边界由应用服务持有。
 */
public interface PaymentOrderRepository {

    Optional<PaymentOrder> findByOrderNo(String orderNo);

    Optional<PaymentOrder> findByUserIdempotency(long userId, String idempotencyKey);

    /**
     * SELECT ... FOR UPDATE；支付/退款等状态迁移由此串行化。
     */
    Optional<PaymentOrder> lockByOrderNo(String orderNo);

    void insert(PaymentOrder order);

    boolean attachPrepay(String orderNo, String codeUrl, String prepayId);

    boolean markPaid(String orderNo, String providerTxnId, Instant paidAt);

    boolean markClosed(String orderNo);

    boolean markFailed(String orderNo);

    /**
     * 原子预占可退金额；超出剩余可退金额或非已支付状态返回 false。
     */
    boolean reserveRefundAmount(String orderNo, int amountCents);

    /**
     * 退款成功后将退满的订单标记为 REFUNDED。
     */
    boolean finalizeRefund(String orderNo);

    /**
     * 渠道退款失败时归还预占金额。
     */
    boolean releaseRefundAmount(String orderNo, int amountCents);

    void updateNotify(String orderNo, String status);

    List<PaymentOrder> findExpired(int limit);

    List<PaymentOrder> findReconcileCandidates(Instant createdBefore, int limit);

    List<PaymentOrder> findPendingNotify(int maxAttempts, int limit);

    void recordNotify(
            String orderNo,
            String channel,
            String eventType,
            String providerTxnId,
            boolean signatureValid,
            boolean processed,
            String message);
}
