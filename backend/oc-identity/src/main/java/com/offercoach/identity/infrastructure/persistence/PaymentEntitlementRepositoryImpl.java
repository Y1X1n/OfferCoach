package com.offercoach.identity.infrastructure.persistence;

import com.offercoach.identity.application.port.PaymentEntitlementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 支付发放幂等登记实现。
 */
@Repository
@RequiredArgsConstructor
public class PaymentEntitlementRepositoryImpl implements PaymentEntitlementRepository {

    private final PaymentEntitlementMapper mapper;

    @Override
    public boolean tryInsert(String orderNo, long userId, String planCode, int amountCents) {
        return mapper.insertIfAbsent(orderNo, userId, planCode, amountCents) > 0;
    }
}
