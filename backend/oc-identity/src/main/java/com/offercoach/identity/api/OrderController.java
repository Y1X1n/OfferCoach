package com.offercoach.identity.api;

import com.offercoach.common.error.BizException;
import com.offercoach.common.error.ErrorCode;
import com.offercoach.identity.api.dto.CreateOrderRequest;
import com.offercoach.identity.api.dto.OrderResponse;
import com.offercoach.identity.api.dto.PayChannelOption;
import com.offercoach.identity.application.payment.PaymentOrderService;
import com.offercoach.identity.security.ClientIpResolver;
import com.offercoach.identity.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 套餐下单与订单查询；需登录，金额与套餐名以服务端配置为准。
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class OrderController {

    private final PaymentOrderService paymentOrderService;
    private final CurrentUser currentUser;
    private final ClientIpResolver clientIpResolver;

    @PostMapping("/orders")
    public ResponseEntity<OrderResponse> create(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) CreateOrderRequest request,
            HttpServletRequest http) {
        if (request == null) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, Map.of("body", "required"));
        }
        OrderResponse response = paymentOrderService.create(
                currentUser.userId(),
                request.planCode(),
                request.channel(),
                idempotencyKey,
                clientIpResolver.resolve(http));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/orders/{orderNo}")
    public OrderResponse get(@PathVariable String orderNo) {
        return paymentOrderService.get(currentUser.userId(), orderNo);
    }

    /**
     * 可用支付渠道；登录后调用，前端据此展示微信 / 支付宝等支付方式。
     */
    @GetMapping("/pay-channels")
    public List<PayChannelOption> payChannels() {
        return paymentOrderService.channels();
    }
}
