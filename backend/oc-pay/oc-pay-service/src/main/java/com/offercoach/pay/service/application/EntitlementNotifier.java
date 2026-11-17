package com.offercoach.pay.service.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offercoach.pay.api.PayDtos;
import com.offercoach.pay.config.PayProperties;
import com.offercoach.pay.service.application.port.PaymentOrderRepository;
import com.offercoach.pay.service.config.PayOpsProperties;
import com.offercoach.pay.service.domain.OrderStatus;
import com.offercoach.pay.service.domain.PaymentOrder;
import com.offercoach.pay.service.support.SimpleHttp;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 支付成功后向 oc-app 下发额度发放通知。
 *
 * <p>通知失败不回滚本侧支付状态（支付事实已成立），改为记录 {@code notify_status=FAILED} 并由扫描任务重试。
 * 通知内容不含任何凭据，仅含订单号、用户、套餐与金额。</p>
 */
@Component
public class EntitlementNotifier {

    private static final Logger log = LoggerFactory.getLogger(EntitlementNotifier.class);
    private static final String STATUS_NOTIFIED = "NOTIFIED";
    private static final String STATUS_FAILED = "FAILED";

    private final PayOpsProperties ops;
    private final PayProperties payProperties;
    private final PaymentOrderRepository orderRepository;
    private final ObjectMapper objectMapper;
    private final SimpleHttp http;

    public EntitlementNotifier(
            PayOpsProperties ops,
            PayProperties payProperties,
            PaymentOrderRepository orderRepository,
            ObjectMapper objectMapper) {
        this.ops = ops;
        this.payProperties = payProperties;
        this.orderRepository = orderRepository;
        this.objectMapper = objectMapper;
        this.http = new SimpleHttp(payProperties.getConnectTimeout());
    }

    /**
     * 下发额度发放通知；已通知或非已支付订单直接跳过。
     */
    public void notify(PaymentOrder order) {
        if (order == null || order.status() != OrderStatus.PAID || STATUS_NOTIFIED.equals(order.notifyStatus())) {
            return;
        }
        String url = trimTrailingSlash(ops.getAppBaseUrl()) + ops.getEntitlementPath();
        PayDtos.EntitlementNotice notice = new PayDtos.EntitlementNotice(
                order.orderNo(),
                order.userId(),
                order.planCode(),
                order.amountCents(),
                order.currency(),
                order.channel() == null ? null : order.channel().code(),
                order.providerTxnId(),
                order.paidAt());
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(payProperties.getInternalHeader(), payProperties.getInternalToken());
        headers.put("Content-Type", "application/json");
        try {
            String body = objectMapper.writeValueAsString(notice);
            HttpResponse<String> response = http.send("POST", url, headers, body, ops.getNotifyTimeout());
            if (response.statusCode() / 100 == 2) {
                orderRepository.updateNotify(order.orderNo(), STATUS_NOTIFIED);
                log.info("额度发放通知成功：orderNo={} userId={}", order.orderNo(), order.userId());
            } else {
                orderRepository.updateNotify(order.orderNo(), STATUS_FAILED);
                log.warn(
                        "额度发放通知失败：orderNo={} status={} body={}",
                        order.orderNo(),
                        response.statusCode(),
                        response.body());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            orderRepository.updateNotify(order.orderNo(), STATUS_FAILED);
            log.warn("额度发放通知被中断：orderNo={}", order.orderNo());
        } catch (Exception exception) {
            orderRepository.updateNotify(order.orderNo(), STATUS_FAILED);
            log.warn("额度发放通知异常：orderNo={} reason={}", order.orderNo(), exception.getMessage());
        }
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
