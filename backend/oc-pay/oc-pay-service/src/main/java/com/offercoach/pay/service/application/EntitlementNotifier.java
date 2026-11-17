package com.offercoach.pay.service.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offercoach.pay.api.PayDtos;
import com.offercoach.pay.config.PayProperties;
import com.offercoach.pay.service.application.port.PaymentOrderRepository;
import com.offercoach.pay.service.config.PayOpsProperties;
import com.offercoach.pay.service.domain.OrderStatus;
import com.offercoach.pay.service.domain.PaymentExceptionKind;
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
 * 重试次数耗尽、或被 oc-app 以 4xx 永久拒绝时置 {@code DEAD} 并写入 {@code payment_exception}：
 * 「已付款但没发额度」必须留下可追查的记录，不允许静默放弃。</p>
 */
@Component
public class EntitlementNotifier {

    private static final Logger log = LoggerFactory.getLogger(EntitlementNotifier.class);
    private static final String STATUS_NOTIFIED = "NOTIFIED";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_DEAD = "DEAD";

    private final PayOpsProperties ops;
    private final PayProperties payProperties;
    private final PaymentOrderRepository orderRepository;
    private final PaymentExceptionRecorder exceptions;
    private final ObjectMapper objectMapper;
    private final SimpleHttp http;

    public EntitlementNotifier(
            PayOpsProperties ops,
            PayProperties payProperties,
            PaymentOrderRepository orderRepository,
            PaymentExceptionRecorder exceptions,
            ObjectMapper objectMapper) {
        this(
                ops,
                payProperties,
                orderRepository,
                exceptions,
                objectMapper,
                new SimpleHttp(payProperties.getConnectTimeout()));
    }

    EntitlementNotifier(
            PayOpsProperties ops,
            PayProperties payProperties,
            PaymentOrderRepository orderRepository,
            PaymentExceptionRecorder exceptions,
            ObjectMapper objectMapper,
            SimpleHttp http) {
        this.ops = ops;
        this.payProperties = payProperties;
        this.orderRepository = orderRepository;
        this.exceptions = exceptions;
        this.objectMapper = objectMapper;
        this.http = http;
    }

    /**
     * 下发额度发放通知；已通知、已死信或非已支付订单直接跳过。
     */
    public void notify(PaymentOrder order) {
        if (order == null
                || order.status() != OrderStatus.PAID
                || STATUS_NOTIFIED.equals(order.notifyStatus())
                || STATUS_DEAD.equals(order.notifyStatus())) {
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
            int status = response.statusCode();
            if (status / 100 == 2) {
                orderRepository.updateNotify(order.orderNo(), STATUS_NOTIFIED, ops.getNotifyMaxAttempts());
                log.info("额度发放通知成功：orderNo={} userId={}", order.orderNo(), order.userId());
            } else if (permanentFailure(status)) {
                dead(order, "oc-app 拒绝发放：status=" + status + " body=" + response.body());
            } else {
                retry(order, "status=" + status + " body=" + response.body());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            retry(order, "通知被中断");
        } catch (Exception exception) {
            retry(order, exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
    }

    /**
     * 4xx 除超时/限流外属于参数或状态不合法，重试不会成功，直接死信。
     */
    private static boolean permanentFailure(int status) {
        return status / 100 == 4 && status != 408 && status != 429;
    }

    private void retry(PaymentOrder order, String reason) {
        int attemptsAfter = order.notifyAttempts() + 1;
        boolean exhausted = attemptsAfter >= ops.getNotifyMaxAttempts();
        orderRepository.updateNotify(
                order.orderNo(), exhausted ? STATUS_DEAD : STATUS_FAILED, ops.getNotifyMaxAttempts());
        if (exhausted) {
            exceptions.record(
                    order.orderNo(),
                    order.userId(),
                    PaymentExceptionKind.ENTITLEMENT_DEAD,
                    "额度发放重试耗尽：" + reason,
                    order.amountCents(),
                    order.channel(),
                    order.providerTxnId());
        } else {
            log.warn("额度发放通知失败，等待重试：orderNo={} attempt={} reason={}", order.orderNo(), attemptsAfter, reason);
        }
    }

    private void dead(PaymentOrder order, String reason) {
        orderRepository.updateNotify(order.orderNo(), STATUS_DEAD, ops.getNotifyMaxAttempts());
        exceptions.record(
                order.orderNo(),
                order.userId(),
                PaymentExceptionKind.ENTITLEMENT_REJECTED,
                reason,
                order.amountCents(),
                order.channel(),
                order.providerTxnId());
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
