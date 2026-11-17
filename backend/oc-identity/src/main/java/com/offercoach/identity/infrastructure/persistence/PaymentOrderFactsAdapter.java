package com.offercoach.identity.infrastructure.persistence;

import com.offercoach.identity.application.port.PaymentOrderFactsPort;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 支付订单事实端口实现：把 Mapper 的 DO 映射为应用层 record。
 */
@Repository
@RequiredArgsConstructor
public class PaymentOrderFactsAdapter implements PaymentOrderFactsPort {

    private final PaymentOrderLookupMapper mapper;

    @Override
    public Optional<Facts> find(String orderNo) {
        PaymentOrderFactsDO row = mapper.findFacts(orderNo);
        if (row == null || row.getUserId() == null) {
            return Optional.empty();
        }
        return Optional.of(new Facts(
                row.getUserId(),
                row.getPlanCode(),
                row.getAmountCents() == null ? 0 : row.getAmountCents(),
                row.getStatus()));
    }
}
