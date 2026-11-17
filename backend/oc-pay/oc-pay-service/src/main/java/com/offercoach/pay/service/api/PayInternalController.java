package com.offercoach.pay.service.api;

import com.offercoach.pay.api.PayDtos;
import com.offercoach.pay.service.application.OrderService;
import com.offercoach.pay.service.channel.ChannelRegistry;
import com.offercoach.pay.service.error.PayErrorKind;
import com.offercoach.pay.service.error.PayException;
import com.offercoach.pay.service.internal.InternalAuthGuard;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部支付接口：仅供 oc-app 调用，需携带 {@code X-OC-Internal-Token}，不暴露公网。
 */
@RestController
@RequestMapping("/internal/v1/pay")
public class PayInternalController {

    private final InternalAuthGuard guard;
    private final OrderService orderService;
    private final ChannelRegistry registry;

    public PayInternalController(InternalAuthGuard guard, OrderService orderService, ChannelRegistry registry) {
        this.guard = guard;
        this.orderService = orderService;
        this.registry = registry;
    }

    @PostMapping("/orders")
    public ResponseEntity<PayDtos.OrderView> createOrder(
            @RequestBody(required = false) PayDtos.CreateOrderRequest request, HttpServletRequest http) {
        guard.require(http);
        if (request == null) {
            throw new PayException(PayErrorKind.VALIDATION_FAILED, "请求体不能为空");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.createOrder(request));
    }

    @GetMapping("/orders/{orderNo}")
    public PayDtos.OrderView getOrder(
            @PathVariable String orderNo, @RequestParam(defaultValue = "0") long userId, HttpServletRequest http) {
        guard.require(http);
        return orderService.getOrder(userId, orderNo);
    }

    @PostMapping("/orders/{orderNo}/close")
    public PayDtos.OrderView closeOrder(
            @PathVariable String orderNo, @RequestParam(defaultValue = "0") long userId, HttpServletRequest http) {
        guard.require(http);
        return orderService.closeOrder(userId, orderNo);
    }

    @PostMapping("/orders/{orderNo}/refund")
    public PayDtos.RefundView refund(
            @PathVariable String orderNo,
            @RequestParam(defaultValue = "0") long userId,
            @RequestBody(required = false) PayDtos.RefundRequest request,
            HttpServletRequest http) {
        guard.require(http);
        if (request == null) {
            throw new PayException(PayErrorKind.VALIDATION_FAILED, "请求体不能为空");
        }
        return orderService.refund(userId, orderNo, request);
    }

    @GetMapping("/channels")
    public PayDtos.ChannelOverview channels(HttpServletRequest http) {
        guard.require(http);
        return registry.overview();
    }
}
