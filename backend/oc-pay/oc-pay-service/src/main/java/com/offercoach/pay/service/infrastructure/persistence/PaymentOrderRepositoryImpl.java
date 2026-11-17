package com.offercoach.pay.service.infrastructure.persistence;

import com.offercoach.pay.service.application.port.PaymentOrderRepository;
import com.offercoach.pay.service.domain.ChannelCode;
import com.offercoach.pay.service.domain.OrderStatus;
import com.offercoach.pay.service.domain.PaymentOrder;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 订单仓储实现：包装 Mapper，并在边界完成 DO 与领域 record 的映射。
 */
@Repository
@RequiredArgsConstructor
public class PaymentOrderRepositoryImpl implements PaymentOrderRepository {

    private final PaymentOrderMapper mapper;

    @Override
    public Optional<PaymentOrder> findByOrderNo(String orderNo) {
        return Optional.ofNullable(toDomain(mapper.findByOrderNo(orderNo)));
    }

    @Override
    public Optional<PaymentOrder> findByUserIdempotency(long userId, String idempotencyKey) {
        return Optional.ofNullable(toDomain(mapper.findByUserIdem(userId, idempotencyKey)));
    }

    @Override
    public Optional<PaymentOrder> lockByOrderNo(String orderNo) {
        return Optional.ofNullable(toDomain(mapper.selectForUpdate(orderNo)));
    }

    @Override
    public void insert(PaymentOrder order) {
        PaymentOrderDO row = new PaymentOrderDO();
        row.setOrderNo(order.orderNo());
        row.setUserId(order.userId());
        row.setPlanCode(order.planCode());
        row.setSubject(order.subject());
        row.setAmountCents(order.amountCents());
        row.setCurrency(order.currency());
        row.setChannel(order.channel().name());
        row.setChannelMethod(order.channel().method());
        row.setStatus(order.status().name());
        row.setIdempotencyKey(order.idempotencyKey());
        row.setNotifyStatus(order.notifyStatus());
        row.setNotifyAttempts(0);
        row.setExpiresAt(toOffset(order.expiresAt()));
        row.setRefundedCents(0);
        mapper.insert(row);
    }

    @Override
    public boolean attachPrepay(String orderNo, String codeUrl, String prepayId) {
        return mapper.attachPrepay(orderNo, codeUrl, prepayId) > 0;
    }

    @Override
    public boolean markPaid(String orderNo, String providerTxnId, Instant paidAt) {
        return mapper.markPaid(orderNo, providerTxnId, toOffset(paidAt == null ? Instant.now() : paidAt)) > 0;
    }

    @Override
    public boolean markClosed(String orderNo) {
        return mapper.markClosed(orderNo) > 0;
    }

    @Override
    public boolean markFailed(String orderNo) {
        return mapper.markFailed(orderNo) > 0;
    }

    @Override
    public boolean reserveRefundAmount(String orderNo, int amountCents) {
        return mapper.reserveRefundAmount(orderNo, amountCents) > 0;
    }

    @Override
    public boolean finalizeRefund(String orderNo) {
        return mapper.finalizeRefund(orderNo) > 0;
    }

    @Override
    public boolean releaseRefundAmount(String orderNo, int amountCents) {
        return mapper.releaseRefundAmount(orderNo, amountCents) > 0;
    }

    @Override
    public void updateNotify(String orderNo, String status) {
        mapper.updateNotify(orderNo, status);
    }

    @Override
    public List<PaymentOrder> findExpired(int limit) {
        return mapper.findExpired(limit).stream()
                .map(PaymentOrderRepositoryImpl::toDomain)
                .toList();
    }

    @Override
    public List<PaymentOrder> findReconcileCandidates(Instant createdBefore, int limit) {
        return mapper.findReconcileCandidates(toOffset(createdBefore), limit).stream()
                .map(PaymentOrderRepositoryImpl::toDomain)
                .toList();
    }

    @Override
    public List<PaymentOrder> findPendingNotify(int maxAttempts, int limit) {
        return mapper.findPendingNotify(maxAttempts, limit).stream()
                .map(PaymentOrderRepositoryImpl::toDomain)
                .toList();
    }

    @Override
    public void recordNotify(
            String orderNo,
            String channel,
            String eventType,
            String providerTxnId,
            boolean signatureValid,
            boolean processed,
            String message) {
        mapper.insertNotifyLog(orderNo, channel, eventType, providerTxnId, signatureValid, processed, message);
    }

    private static PaymentOrder toDomain(PaymentOrderDO row) {
        if (row == null) {
            return null;
        }
        return new PaymentOrder(
                row.getId() == null ? 0L : row.getId(),
                row.getOrderNo(),
                row.getUserId() == null ? 0L : row.getUserId(),
                row.getPlanCode(),
                row.getSubject(),
                row.getAmountCents() == null ? 0 : row.getAmountCents(),
                row.getCurrency(),
                parseChannel(row.getChannel()),
                parseStatus(row.getStatus()),
                row.getProviderTxnId(),
                row.getPrepayId(),
                row.getCodeUrl(),
                row.getIdempotencyKey(),
                row.getNotifyStatus(),
                row.getNotifyAttempts() == null ? 0 : row.getNotifyAttempts(),
                toInstant(row.getExpiresAt()),
                toInstant(row.getPaidAt()),
                toInstant(row.getClosedAt()),
                row.getRefundedCents() == null ? 0 : row.getRefundedCents(),
                toInstant(row.getCreatedAt()),
                toInstant(row.getUpdatedAt()));
    }

    private static ChannelCode parseChannel(String value) {
        return value == null ? null : ChannelCode.valueOf(value);
    }

    private static OrderStatus parseStatus(String value) {
        return value == null ? null : OrderStatus.valueOf(value);
    }

    private static Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime toOffset(Instant value) {
        return value == null ? null : OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }
}
