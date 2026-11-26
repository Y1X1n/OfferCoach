package com.offercoach.pay.service.infrastructure.persistence;

import com.offercoach.pay.service.application.port.PaymentRefundRepository;
import com.offercoach.pay.service.domain.PaymentRefund;
import com.offercoach.pay.service.domain.RefundStatus;
import java.time.OffsetDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 退款仓储实现。
 */
@Repository
@RequiredArgsConstructor
public class PaymentRefundRepositoryImpl implements PaymentRefundRepository {

    private final PaymentRefundMapper mapper;

    @Override
    public Optional<PaymentRefund> findByOrderIdempotency(String orderNo, String idempotencyKey) {
        return Optional.ofNullable(toDomain(mapper.findByOrderIdem(orderNo, idempotencyKey)));
    }

    @Override
    public boolean insertIfAbsent(PaymentRefund refund) {
        return mapper.insertIfAbsent(
                        refund.refundNo(),
                        refund.orderNo(),
                        refund.userId(),
                        refund.amountCents(),
                        refund.status().name(),
                        refund.reason(),
                        refund.idempotencyKey())
                > 0;
    }

    @Override
    public boolean updateResult(String refundNo, RefundStatus status, String channelRefundId) {
        return mapper.updateResult(refundNo, status.name(), channelRefundId) > 0;
    }

    private static PaymentRefund toDomain(PaymentRefundDO row) {
        if (row == null) {
            return null;
        }
        return new PaymentRefund(
                row.getId() == null ? 0L : row.getId(),
                row.getRefundNo(),
                row.getOrderNo(),
                row.getUserId() == null ? 0L : row.getUserId(),
                row.getAmountCents() == null ? 0 : row.getAmountCents(),
                RefundStatus.valueOf(row.getStatus()),
                row.getChannelRefundId(),
                row.getReason(),
                row.getIdempotencyKey(),
                toInstant(row.getCreatedAt()),
                toInstant(row.getUpdatedAt()));
    }

    private static java.time.Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
