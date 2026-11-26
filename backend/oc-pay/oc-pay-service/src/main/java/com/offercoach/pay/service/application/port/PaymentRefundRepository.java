package com.offercoach.pay.service.application.port;

import com.offercoach.pay.service.domain.PaymentRefund;
import com.offercoach.pay.service.domain.RefundStatus;
import java.util.Optional;

/**
 * 退款仓储端口。
 */
public interface PaymentRefundRepository {

    Optional<PaymentRefund> findByOrderIdempotency(String orderNo, String idempotencyKey);

    /**
     * 幂等插入退款单；返回 true 表示本次新建，false 表示幂等键已存在（不抛异常）。
     */
    boolean insertIfAbsent(PaymentRefund refund);

    boolean updateResult(String refundNo, RefundStatus status, String channelRefundId);
}
