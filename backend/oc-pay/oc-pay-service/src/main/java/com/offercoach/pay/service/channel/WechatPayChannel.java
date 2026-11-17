package com.offercoach.pay.service.channel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.offercoach.pay.config.PayProperties;
import com.offercoach.pay.service.config.PayChannelProperties;
import com.offercoach.pay.service.config.PayOpsProperties;
import com.offercoach.pay.service.crypto.AesGcm;
import com.offercoach.pay.service.crypto.Pem;
import com.offercoach.pay.service.crypto.RsaSigner;
import com.offercoach.pay.service.domain.ChannelCode;
import com.offercoach.pay.service.domain.PaymentOrder;
import com.offercoach.pay.service.domain.PaymentRefund;
import com.offercoach.pay.service.error.PayErrorKind;
import com.offercoach.pay.service.error.PayException;
import com.offercoach.pay.service.support.Headers;
import com.offercoach.pay.service.support.SimpleHttp;
import java.net.http.HttpResponse;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 微信支付 v3（Native 扫码）渠道适配。
 *
 * <p>请求以商户私钥做 SHA256-RSA 签名；回调以微信平台证书验签并解密 resource（AES-256-GCM）。
 * 商户私钥 / 平台证书 / APIv3 密钥均来自部署配置，不落库、不打日志。</p>
 */
@Component
public class WechatPayChannel implements PaymentChannel {

    private static final Logger log = LoggerFactory.getLogger(WechatPayChannel.class);
    private static final String NATIVE_PATH = "/v3/pay/transactions/native";
    private static final String REFUND_PATH = "/v3/refund/domestic/refunds";
    private static final DateTimeFormatter RFC3339 =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX").withZone(ZoneOffset.ofHours(8));

    private final PayChannelProperties properties;
    private final PayOpsProperties ops;
    private final ObjectMapper objectMapper;
    private final PayProperties payProperties;
    private final SimpleHttp http;

    private volatile PrivateKey merchantKey;
    private volatile PublicKey platformKey;

    public WechatPayChannel(
            PayChannelProperties properties,
            PayOpsProperties ops,
            ObjectMapper objectMapper,
            PayProperties payProperties) {
        this.properties = properties;
        this.ops = ops;
        this.objectMapper = objectMapper;
        this.payProperties = payProperties;
        this.http = new SimpleHttp(payProperties.getConnectTimeout());
    }

    @Override
    public ChannelCode code() {
        return ChannelCode.WECHAT;
    }

    @Override
    public boolean available() {
        PayChannelProperties.Wechat wechat = properties.getWechat();
        return wechat.isEnabled()
                && hasText(wechat.getAppId())
                && hasText(wechat.getMchId())
                && hasText(wechat.getMerchantSerialNo())
                && hasText(wechat.getApiV3Key())
                && (hasText(wechat.getPrivateKeyPath()) || hasText(wechat.getPrivateKeyPem()));
    }

    @Override
    public String notifyPath() {
        return properties.getWechat().getNotifyPath();
    }

    @Override
    public Prepay prepay(PaymentOrder order, String clientIp) {
        requireAvailable();
        String notifyUrl = notifyUrl();
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("appid", properties.getWechat().getAppId());
        payload.put("mchid", properties.getWechat().getMchId());
        payload.put("description", order.subject());
        payload.put("out_trade_no", order.orderNo());
        payload.put("notify_url", notifyUrl);
        if (order.expiresAt() != null) {
            payload.put("time_expire", RFC3339.format(order.expiresAt()));
        }
        payload.putObject("amount").put("total", order.amountCents()).put("currency", order.currency());
        JsonNode response = call("POST", NATIVE_PATH, payload);
        String codeUrl = response.path("code_url").asText("");
        if (codeUrl.isBlank()) {
            throw new PayException(PayErrorKind.UPSTREAM_FAILED, "微信下单未返回二维码链接");
        }
        return new Prepay(codeUrl, null);
    }

    @Override
    public ChannelOrderState query(PaymentOrder order) {
        requireAvailable();
        String path = "/v3/pay/transactions/out-trade-no/" + order.orderNo() + "?mchid="
                + properties.getWechat().getMchId();
        JsonNode response = call("GET", path, null);
        String tradeState = response.path("trade_state").asText("");
        String txnId = response.path("transaction_id").asText("");
        Instant paidAt = parseInstant(response.path("success_time").asText(""));
        JsonNode total = response.path("amount").path("total");
        Integer amountCents = total.isNumber() ? total.asInt() : null;
        return new ChannelOrderState("SUCCESS".equals(tradeState), txnId, paidAt, tradeState, amountCents);
    }

    @Override
    public boolean close(PaymentOrder order) {
        if (!available()) {
            return false;
        }
        try {
            String path = "/v3/pay/transactions/out-trade-no/" + order.orderNo() + "/close";
            ObjectNode payload = objectMapper
                    .createObjectNode()
                    .put("mchid", properties.getWechat().getMchId());
            call("POST", path, payload);
            return true;
        } catch (PayException exception) {
            log.warn("微信关单未确认，订单保留待支付等待重试：orderNo={} reason={}", order.orderNo(), exception.getMessage());
            return false;
        }
    }

    @Override
    public RefundOutcome refund(PaymentOrder order, PaymentRefund refund) {
        requireAvailable();
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("out_trade_no", order.orderNo());
        payload.put("out_refund_no", refund.refundNo());
        if (refund.reason() != null && !refund.reason().isBlank()) {
            payload.put("reason", refund.reason());
        }
        payload.putObject("amount")
                .put("refund", refund.amountCents())
                .put("total", order.amountCents())
                .put("currency", order.currency());
        payload.put("notify_url", notifyUrl());
        JsonNode response = call("POST", REFUND_PATH, payload);
        String status = response.path("status").asText("");
        String refundId = response.path("refund_id").asText("");
        boolean success = "SUCCESS".equals(status) || "PROCESSING".equals(status);
        return new RefundOutcome(
                success, refundId, status, response.path("message").asText(""));
    }

    @Override
    public ChannelNotify parseNotify(Map<String, String> headers, String body) {
        PayChannelProperties.Wechat wechat = properties.getWechat();
        String timestamp = Headers.get(headers, "Wechatpay-Timestamp");
        String nonce = Headers.get(headers, "Wechatpay-Nonce");
        String signature = Headers.get(headers, "Wechatpay-Signature");
        if (timestamp == null || nonce == null || signature == null) {
            throw new PayException(PayErrorKind.VALIDATION_FAILED, "微信回调缺少签名头");
        }
        requireFreshTimestamp(timestamp);
        PublicKey key = platformKey();
        boolean valid = key != null && RsaSigner.verify(key, timestamp + "\n" + nonce + "\n" + body + "\n", signature);
        if (!valid) {
            throw new PayException(PayErrorKind.VALIDATION_FAILED, "微信回调验签失败");
        }
        JsonNode root = parse(body);
        JsonNode resource = root.path("resource");
        String plain = AesGcm.decryptToString(
                wechat.getApiV3Key(),
                resource.path("nonce").asText(""),
                resource.path("associated_data").asText(""),
                resource.path("ciphertext").asText(""));
        JsonNode data = parse(plain);
        String tradeState = data.path("trade_state").asText("");
        Integer amount = data.path("amount").path("total").isNumber()
                ? data.path("amount").path("total").asInt()
                : null;
        return new ChannelNotify(
                data.path("out_trade_no").asText(""),
                data.path("transaction_id").asText(""),
                "SUCCESS".equals(tradeState),
                amount,
                tradeState,
                root.path("event_type").asText(""),
                parseInstant(data.path("success_time").asText("")));
    }

    @Override
    public String successResponseBody() {
        return "{\"code\":\"SUCCESS\",\"message\":\"成功\"}";
    }

    private JsonNode call(String method, String pathWithQuery, JsonNode body) {
        String json = body == null ? "" : write(body);
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        String nonce = AesGcm.nonce(32);
        String message = method + "\n" + pathWithQuery + "\n" + timestamp + "\n" + nonce + "\n" + json + "\n";
        String signature = RsaSigner.signBase64(merchantPrivateKey(), message);
        String authorization =
                "WECHATPAY2-SHA256-RSA2048 mchid=\"" + properties.getWechat().getMchId()
                        + "\",nonce_str=\"" + nonce + "\",signature=\"" + signature + "\",timestamp=\"" + timestamp
                        + "\",serial_no=\"" + properties.getWechat().getMerchantSerialNo() + "\"";
        Map<String, String> requestHeaders = new LinkedHashMap<>();
        requestHeaders.put("Authorization", authorization);
        requestHeaders.put("Accept", "application/json");
        requestHeaders.put("User-Agent", "oc-pay/1.0");
        if (body != null) {
            requestHeaders.put("Content-Type", "application/json");
        }
        HttpResponse<String> response;
        try {
            response = http.send(
                    method,
                    baseUrl() + pathWithQuery,
                    requestHeaders,
                    body == null ? null : json,
                    payProperties.getRequestTimeout());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PayException(PayErrorKind.UPSTREAM_FAILED, "微信请求被中断", exception);
        } catch (Exception exception) {
            throw new PayException(PayErrorKind.UPSTREAM_FAILED, "微信请求失败", exception);
        }
        verifyResponseSignature(response);
        if (response.statusCode() / 100 != 2) {
            throw new PayException(
                    PayErrorKind.UPSTREAM_FAILED, upstreamMessage(response.body(), response.statusCode()));
        }
        return parse(response.body());
    }

    /**
     * 校验响应签名；未配置平台证书时跳过并告警（生产应配置）。
     */
    private void verifyResponseSignature(HttpResponse<String> response) {
        PublicKey key = platformKey();
        if (key == null) {
            log.warn("未配置微信平台证书，跳过响应验签");
            return;
        }
        String timestamp = response.headers().firstValue("Wechatpay-Timestamp").orElse(null);
        String nonce = response.headers().firstValue("Wechatpay-Nonce").orElse(null);
        String signature = response.headers().firstValue("Wechatpay-Signature").orElse(null);
        if (timestamp == null || nonce == null || signature == null) {
            return;
        }
        if (!RsaSigner.verify(key, timestamp + "\n" + nonce + "\n" + response.body() + "\n", signature)) {
            throw new PayException(PayErrorKind.UPSTREAM_FAILED, "微信响应验签失败");
        }
    }

    private String notifyUrl() {
        String base = ops.getPublicBaseUrl();
        if (base == null || base.isBlank()) {
            throw new PayException(PayErrorKind.NOT_CONFIGURED, "未配置对外回调基地址（oc.pay.ops.public-base-url）");
        }
        return base.replaceAll("/+$", "") + properties.getWechat().getNotifyPath();
    }

    private void requireAvailable() {
        if (!available()) {
            throw new PayException(PayErrorKind.NOT_CONFIGURED, "微信支付未启用或凭据不全");
        }
    }

    /**
     * 回调时间戳窗口校验：签名覆盖时间戳，超出窗口的旧报文视为重放。
     *
     * <p>微信重推每次都会带新时间戳，因此窗口只影响重放，不影响正常重试。</p>
     */
    private void requireFreshTimestamp(String timestamp) {
        long seconds;
        try {
            seconds = Long.parseLong(timestamp.strip());
        } catch (NumberFormatException exception) {
            throw new PayException(PayErrorKind.VALIDATION_FAILED, "微信回调时间戳非法");
        }
        long skew = Math.abs(Instant.now().getEpochSecond() - seconds);
        if (skew > ops.getNotifyMaxSkewSeconds()) {
            throw new PayException(PayErrorKind.VALIDATION_FAILED, "微信回调时间戳超出允许窗口");
        }
    }

    private PrivateKey merchantPrivateKey() {
        PrivateKey key = merchantKey;
        if (key == null) {
            synchronized (this) {
                if (merchantKey == null) {
                    PayChannelProperties.Wechat wechat = properties.getWechat();
                    merchantKey = Pem.privateKey(wechat.getPrivateKeyPath(), wechat.getPrivateKeyPem());
                }
                key = merchantKey;
            }
        }
        return key;
    }

    private PublicKey platformKey() {
        PublicKey key = platformKey;
        if (key == null) {
            synchronized (this) {
                if (platformKey == null) {
                    PayChannelProperties.Wechat wechat = properties.getWechat();
                    try {
                        if (hasText(wechat.getPlatformCertPath())) {
                            platformKey = Pem.certificatePublicKey(wechat.getPlatformCertPath(), null);
                        } else if (hasText(wechat.getPlatformPublicKeyPath())
                                || hasText(wechat.getPlatformPublicKeyPem())) {
                            platformKey =
                                    Pem.publicKey(wechat.getPlatformPublicKeyPath(), wechat.getPlatformPublicKeyPem());
                        }
                    } catch (Pem.PemException exception) {
                        log.warn("微信平台证书读取失败：{}", exception.getMessage());
                    }
                }
                key = platformKey;
            }
        }
        return key;
    }

    private JsonNode parse(String json) {
        try {
            return objectMapper.readTree(json == null || json.isBlank() ? "{}" : json);
        } catch (Exception exception) {
            throw new PayException(PayErrorKind.UPSTREAM_FAILED, "微信返回体解析失败", exception);
        }
    }

    private String write(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception exception) {
            throw new PayException(PayErrorKind.INTERNAL_ERROR, "微信请求体序列化失败", exception);
        }
    }

    private String upstreamMessage(String body, int status) {
        try {
            JsonNode node = objectMapper.readTree(body);
            String message = node.path("message").asText("");
            if (!message.isBlank()) {
                return "微信返回 " + status + "：" + message;
            }
        } catch (Exception ignored) {
            // 非 JSON 错误体，退回状态码
        }
        return "微信返回 " + status;
    }

    private String baseUrl() {
        String value = properties.getWechat().getApiBaseUrl();
        return value == null || value.isBlank() ? "https://api.mch.weixin.qq.com" : value.replaceAll("/+$", "");
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (Exception exception) {
            return null;
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
