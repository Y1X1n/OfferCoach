package com.offercoach.pay.client;

import com.offercoach.common.trace.TraceContext;
import com.offercoach.pay.api.PayDtos;
import com.offercoach.pay.config.PayProperties;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * 调用 oc-pay-service 的客户端；oc-app 是它唯一的调用方。
 *
 * <p>业务判断（套餐校验、额度发放、归属校验）留在 oc-app；本客户端只做请求封装与错误映射。
 * 所有调用透传当前请求的 {@code X-OC-Request-Id} 以串联跨服务 Trace。</p>
 */
@Component
public class PayGateway {

    private static final Logger log = LoggerFactory.getLogger(PayGateway.class);
    private static final int MAX_ERROR_BODY = 512;
    private static final String ORDERS_PATH = "/internal/v1/pay/orders";
    private static final String CHANNELS_PATH = "/internal/v1/pay/channels";

    private final PayProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public PayGateway(PayProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /**
     * 创建订单；同一 idempotencyKey 重复调用返回首次订单。
     */
    public PayDtos.OrderView createOrder(PayDtos.CreateOrderRequest request) {
        return send(ORDERS_PATH, "POST", request.userId(), request, PayDtos.OrderView.class);
    }

    /**
     * 查询订单；userId 用于服务端归属校验，非本人订单按不存在处理。
     */
    public PayDtos.OrderView getOrder(String orderNo, long userId) {
        return send(
                ORDERS_PATH + "/" + encode(orderNo) + "?userId=" + userId,
                "GET",
                userId,
                null,
                PayDtos.OrderView.class);
    }

    /**
     * 小程序内支付：授权码换买家标识并创建交易，返回可被 my.tradePay 调起的交易号。
     */
    public PayDtos.MiniPayView miniPay(String orderNo, long userId, String authCode) {
        return send(
                ORDERS_PATH + "/" + encode(orderNo) + "/miniapp?userId=" + userId,
                "POST",
                userId,
                new PayDtos.MiniPayRequest(authCode),
                PayDtos.MiniPayView.class);
    }

    /**
     * 关闭未支付订单。
     */
    public PayDtos.OrderView closeOrder(String orderNo, long userId) {
        return send(ORDERS_PATH + "/" + encode(orderNo) + "/close", "POST", userId, null, PayDtos.OrderView.class);
    }

    /**
     * 发起退款。
     */
    public PayDtos.RefundView refund(String orderNo, long userId, PayDtos.RefundRequest request) {
        return send(ORDERS_PATH + "/" + encode(orderNo) + "/refund", "POST", userId, request, PayDtos.RefundView.class);
    }

    /**
     * 渠道可用性；前端据此决定展示哪些支付方式。
     */
    public PayDtos.ChannelOverview channels() {
        return send(CHANNELS_PATH, "GET", 0L, null, PayDtos.ChannelOverview.class);
    }

    private <T> T send(String path, String method, long userId, Object body, Class<T> responseType) {
        String base = trimTrailingSlash(properties.getBaseUrl());
        if (base.isBlank()) {
            throw new PayClientException("支付服务地址未配置", 0);
        }
        Duration timeout = properties.getRequestTimeout();
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(base + path))
                .timeout(timeout)
                .header(properties.getInternalHeader(), properties.getInternalToken())
                .header(properties.getUserIdHeader(), Long.toString(userId))
                .header(properties.getRequestIdHeader(), currentRequestId());
        if (body == null) {
            builder.method(method.toUpperCase(), HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json")
                    .method(
                            method.toUpperCase(),
                            HttpRequest.BodyPublishers.ofString(writeJson(body), StandardCharsets.UTF_8));
        }
        HttpResponse<String> response;
        try {
            response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException exception) {
            throw new PayClientException("支付服务调用失败", 0, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PayClientException("支付服务调用被中断", 0, exception);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.warn("支付服务返回非 2xx：status={} body={}", response.statusCode(), truncate(response.body()));
            throw new PayClientException(errorMessage(response.body(), response.statusCode()), response.statusCode());
        }
        return readJson(response.body(), responseType);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new PayClientException("支付请求序列化失败", 0, exception);
        }
    }

    private <T> T readJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JacksonException exception) {
            throw new PayClientException("支付响应解析失败", 0, exception);
        }
    }

    /**
     * 支付服务的错误体带稳定 {@code message}；提取失败时退回状态码描述。
     */
    private String errorMessage(String body, int status) {
        if (body != null && !body.isBlank()) {
            try {
                String message = objectMapper.readTree(body).path("message").asText("");
                if (!message.isBlank()) {
                    return message;
                }
            } catch (JacksonException ignored) {
                // 非 JSON 错误体，退回状态码描述
            }
        }
        return "支付服务返回 " + status;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String currentRequestId() {
        String requestId = TraceContext.requestId();
        return requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId;
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= MAX_ERROR_BODY ? value : value.substring(0, MAX_ERROR_BODY);
    }
}
