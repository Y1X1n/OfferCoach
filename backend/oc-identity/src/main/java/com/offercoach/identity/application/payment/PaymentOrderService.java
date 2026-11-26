package com.offercoach.identity.application.payment;

import com.offercoach.common.error.BizException;
import com.offercoach.common.error.ErrorCode;
import com.offercoach.identity.api.dto.OrderResponse;
import com.offercoach.identity.api.dto.PayChannelOption;
import com.offercoach.identity.application.port.QuotaRepository;
import com.offercoach.identity.domain.quota.SubscriptionPlan;
import com.offercoach.pay.api.PayDtos;
import com.offercoach.pay.client.PayClientException;
import com.offercoach.pay.client.PayGateway;
import java.util.List;
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
    private static final String STATUS_CREATED = "CREATED";

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
            throw new BizException(ErrorCode.PAY_UNAVAILABLE, Map.of("reason", exception.getMessage()));
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
            throw new BizException(ErrorCode.PAY_UNAVAILABLE, Map.of("reason", exception.getMessage()));
        }
    }

    /**
     * 关闭未支付订单；已支付/已关闭等终态订单直接返回当前视图，重复调用幂等。
     *
     * <p>oc-pay 在渠道关单失败时会返回错误，这里不吞异常，按既有错误体透出给前端。</p>
     */
    public OrderResponse cancel(long userId, String orderNo) {
        PayDtos.OrderView current;
        try {
            current = payGateway.getOrder(orderNo, userId);
        } catch (PayClientException exception) {
            if (exception.status() == 404) {
                throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("orderNo", orderNo));
            }
            log.warn("取消前查询订单失败：orderNo={} reason={}", orderNo, exception.getMessage());
            throw new BizException(ErrorCode.PAY_UNAVAILABLE, Map.of("reason", exception.getMessage()));
        }
        if (!STATUS_CREATED.equals(current.status())) {
            return OrderResponse.from(current);
        }
        try {
            return OrderResponse.from(payGateway.closeOrder(orderNo, userId));
        } catch (PayClientException exception) {
            if (exception.status() == 404) {
                throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("orderNo", orderNo));
            }
            if (exception.status() == 409) {
                // 渠道关单未确认：订单仍可支付，必须让用户看到「关闭失败」而不是静默成功
                log.warn("渠道关单未确认，订单保持待支付：orderNo={} reason={}", orderNo, exception.getMessage());
                throw new BizException(ErrorCode.STATE_CONFLICT, Map.of("reason", exception.getMessage()));
            }
            log.warn("订单关闭失败：orderNo={} reason={}", orderNo, exception.getMessage());
            throw new BizException(ErrorCode.PAY_UNAVAILABLE, Map.of("reason", exception.getMessage()));
        }
    }

    /**
     * 支付渠道可用性；前端据此决定展示哪些支付方式。
     */
    public List<PayChannelOption> channels() {
        try {
            return payGateway.channels().channels().stream()
                    .map(info -> new PayChannelOption(info.channel(), info.enabled(), info.method()))
                    .toList();
        } catch (PayClientException exception) {
            log.warn("支付渠道查询失败：reason={}", exception.getMessage());
            throw new BizException(ErrorCode.PAY_UNAVAILABLE, Map.of("reason", exception.getMessage()));
        }
    }
}
