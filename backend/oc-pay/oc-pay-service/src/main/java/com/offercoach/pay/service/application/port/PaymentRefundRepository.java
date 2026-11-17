package com.offercoach.pay.service.application.port;

import com.offercoach.pay.service.domain.PaymentRefund;
import com.offercoach.pay.service.domain.RefundStatus;
import java.util.Optional;

/**
 * 退款仓储端口。
 */
public interface PaymentRefundRepository {

    Optional<PaymentRefund> findByOrderIdempotency(String orderNo, String idempotencyKey);

    void insert(PaymentRefund refund);

    boolean updateResult(String refundNo, RefundStatus status, String channelRefundId);
}
