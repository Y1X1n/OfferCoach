package com.offercoach.identity.application.payment;

import com.offercoach.common.error.BizException;
import com.offercoach.common.error.ErrorCode;
import com.offercoach.identity.api.dto.OrderResponse;
import com.offercoach.identity.application.port.QuotaRepository;
import com.offercoach.identity.domain.quota.SubscriptionPlan;
import com.offercoach.pay.api.PayDtos;
import com.offercoach.pay.client.PayClientException;
import com.offercoach.pay.client.PayGateway;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 下单用例：校验套餐可购买后委托 oc-pay 创建渠道订单。
 *
 * <p>金额与套餐名以服务端订阅配置为准，前端只传 planCode 与渠道，避免篡改金额。
 * 幂等键由前端提供，与用户 ID 组合后透传 oc-pay。</p>
 */
@Service
@RequiredArgsConstructor
public class PaymentOrderService {

    private static final Logger log = LoggerFactory.getLogger(PaymentOrderService.class);

    private final QuotaRepository quotaRepository;
    private final PayGateway payGateway;

    public OrderResponse create(long userId, String planCode, String channel, String idempotencyKey, String clientIp) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, Map.of("header", "Idempotency-Key"));
        }
        if (planCode == null || planCode.isBlank()) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, Map.of("field", "planCode"));
        }
        if (channel == null || channel.isBlank()) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, Map.of("field", "channel"));
        }
        SubscriptionPlan plan = quotaRepository
                .findPlan(planCode.strip())
                .filter(SubscriptionPlan::active)
                .orElseThrow(() -> new BizException(ErrorCode.VALIDATION_FAILED, Map.of("field", "planCode")));
        if (plan.priceCents() <= 0) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, Map.of("planCode", plan.code()));
        }
        PayDtos.CreateOrderRequest request = new PayDtos.CreateOrderRequest(
                userId,
                plan.code(),
                plan.name(),
                plan.priceCents(),
                "CNY",
                channel.strip().toLowerCase(Locale.ROOT),
                "oc:" + userId + ":" + idempotencyKey.strip(),
                clientIp);
        try {
            return OrderResponse.from(payGateway.createOrder(request));
        } catch (PayClientException exception) {
            log.warn("下单失败：userId={} planCode={} reason={}", userId, plan.code(), exception.getMessage());
            throw new BizException(ErrorCode.INTERNAL_ERROR, Map.of("reason", exception.getMessage()));
        }
    }

    public OrderResponse get(long userId, String orderNo) {
        try {
            return OrderResponse.from(payGateway.getOrder(orderNo, userId));
        } catch (PayClientException exception) {
            if (exception.status() == 404) {
                throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("orderNo", orderNo));
            }
            log.warn("订单查询失败：orderNo={} reason={}", orderNo, exception.getMessage());
            throw new BizException(ErrorCode.INTERNAL_ERROR, Map.of("reason", exception.getMessage()));
        }
    }
}
