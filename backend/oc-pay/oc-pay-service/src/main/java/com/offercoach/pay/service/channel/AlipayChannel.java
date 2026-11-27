package com.offercoach.pay.service.channel;

import com.offercoach.pay.config.PayProperties;
import com.offercoach.pay.service.config.PayChannelProperties;
import com.offercoach.pay.service.config.PayOpsProperties;
import com.offercoach.pay.service.crypto.Pem;
import com.offercoach.pay.service.crypto.RsaSigner;
import com.offercoach.pay.service.domain.ChannelCode;
import com.offercoach.pay.service.domain.PaymentOrder;
import com.offercoach.pay.service.domain.PaymentRefund;
import com.offercoach.pay.service.error.PayErrorKind;
import com.offercoach.pay.service.error.PayException;
import com.offercoach.pay.service.support.SimpleHttp;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 支付宝渠道适配（{@code alipay.trade.precreate} 扫码支付为主）。
 *
 * <p>请求以应用私钥做 RSA2 签名；回调以支付宝公钥验签。网关响应依赖 TLS 通道，
 * 不做逐条响应验签（如需可再加）；商户私钥 / 支付宝公钥来自部署配置。</p>
 */
@Component
public class AlipayChannel implements PaymentChannel, MiniAppPaymentChannel {

    private static final Logger log = LoggerFactory.getLogger(AlipayChannel.class);
    private static final String SUCCESS_CODE = "10000";
    private static final String PRECREATE_METHOD = "alipay.trade.precreate";
    private static final String MINI_CREATE_METHOD = "alipay.trade.create";
    private static final String OAUTH_METHOD = "alipay.system.oauth.token";
    private static final String QUERY_METHOD = "alipay.trade.query";
    private static final String CLOSE_METHOD = "alipay.trade.close";
    private static final String REFUND_METHOD = "alipay.trade.refund";
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.ofHours(8));

    private final PayChannelProperties properties;
    private final PayOpsProperties ops;
    private final ObjectMapper objectMapper;
    private final PayProperties payProperties;
    private final SimpleHttp http;

    private volatile PrivateKey appPrivateKey;
    private volatile PublicKey alipayPublicKey;

    public AlipayChannel(
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
        return ChannelCode.ALIPAY;
    }

    @Override
    public boolean available() {
        PayChannelProperties.Alipay alipay = properties.getAlipay();
        return alipay.isEnabled()
                && hasText(alipay.getAppId())
                && (hasText(alipay.getPrivateKeyPath()) || hasText(alipay.getPrivateKeyPem()));
    }

    @Override
    public String notifyPath() {
        return properties.getAlipay().getNotifyPath();
    }

    @Override
    public Prepay prepay(PaymentOrder order, String clientIp) {
        requireAvailable();
        ObjectNode biz = objectMapper.createObjectNode();
        biz.put("out_trade_no", order.orderNo());
        biz.put("total_amount", yuan(order.amountCents()));
        biz.put("subject", order.subject());
        if (order.expiresAt() != null) {
            biz.put("timeout_express", "30m");
        }
        JsonNode response = invoke(PRECREATE_METHOD, biz);
        String codeUrl = response.path("qr_code").asText("");
        if (codeUrl.isBlank()) {
            throw new PayException(PayErrorKind.UPSTREAM_FAILED, "支付宝下单未返回二维码链接");
        }
        return new Prepay(codeUrl, response.path("out_trade_no").asText(""));
    }

    /**
     * 小程序内支付：用 {@code my.getAuthCode} 的授权码换买家标识，再创建可被 {@code my.tradePay} 调起的交易。
     *
     * <p>授权码一次性、有效期很短；失败按上游失败透出，由客户端提示用户重试。</p>
     */
    @Override
    public String miniPrepay(PaymentOrder order, String authCode) {
        requireAvailable();
        if (!hasText(authCode)) {
            throw new PayException(PayErrorKind.VALIDATION_FAILED, "缺少支付宝授权码");
        }
        String buyerId = alipayBuyerId(authCode);
        ObjectNode biz = objectMapper.createObjectNode();
        biz.put("out_trade_no", order.orderNo());
        biz.put("total_amount", yuan(order.amountCents()));
        biz.put("subject", order.subject());
        biz.put("buyer_id", buyerId);
        if (order.expiresAt() != null) {
            biz.put("timeout_express", "30m");
        }
        JsonNode response = invoke(MINI_CREATE_METHOD, biz);
        String tradeNo = response.path("trade_no").asText("");
        if (tradeNo.isBlank()) {
            throw new PayException(PayErrorKind.UPSTREAM_FAILED, "支付宝下单未返回交易号");
        }
        log.info("支付宝小程序下单成功：orderNo={} tradeNo={}", order.orderNo(), tradeNo);
        return tradeNo;
    }

    /**
     * 授权码换支付宝用户号；该接口不接受 {@code notify_url}，也不涉及业务参数。
     */
    private String alipayBuyerId(String authCode) {
        Map<String, String> extra = new TreeMap<>();
        extra.put("grant_type", "authorization_code");
        extra.put("code", authCode);
        JsonNode response = invoke(OAUTH_METHOD, null, extra, false);
        String userId = response.path("user_id").asText("");
        if (userId.isBlank()) {
            userId = response.path("open_id").asText("");
        }
        if (userId.isBlank()) {
            throw new PayException(PayErrorKind.UPSTREAM_FAILED, "未能从支付宝授权码取得用户标识");
        }
        return userId;
    }

    @Override
    public ChannelOrderState query(PaymentOrder order) {
        requireAvailable();
        ObjectNode biz = objectMapper.createObjectNode().put("out_trade_no", order.orderNo());
        JsonNode response = invoke(QUERY_METHOD, biz);
        String tradeState = response.path("trade_status").asText("");
        String txnId = response.path("trade_no").asText("");
        Instant paidAt = parseInstant(response.path("send_pay_date").asText(""));
        JsonNode total = response.path("total_amount");
        Integer amountCents = total.isTextual() ? cents(total.asText()) : null;
        return new ChannelOrderState(isPaidTradeState(tradeState), txnId, paidAt, tradeState, amountCents);
    }

    @Override
    public boolean close(PaymentOrder order) {
        if (!available()) {
            return false;
        }
        try {
            ObjectNode biz = objectMapper.createObjectNode().put("out_trade_no", order.orderNo());
            invoke(CLOSE_METHOD, biz);
            return true;
        } catch (PayException exception) {
            log.warn("支付宝关单未确认，订单保留待支付等待重试：orderNo={} reason={}", order.orderNo(), exception.getMessage());
            return false;
        }
    }

    @Override
    public RefundOutcome refund(PaymentOrder order, PaymentRefund refund) {
        requireAvailable();
        ObjectNode biz = objectMapper.createObjectNode();
        biz.put("out_trade_no", order.orderNo());
        biz.put("out_request_no", refund.refundNo());
        biz.put("refund_amount", yuan(refund.amountCents()));
        if (refund.reason() != null && !refund.reason().isBlank()) {
            biz.put("refund_reason", refund.reason());
        }
        JsonNode response = invoke(REFUND_METHOD, biz);
        String txnId = response.path("trade_no").asText("");
        return new RefundOutcome(true, txnId, response.path("fund_change").asText(""), "");
    }

    @Override
    public ChannelNotify parseNotify(Map<String, String> headers, String body) {
        Map<String, String> params = parseForm(body);
        String sign = params.get("sign");
        PublicKey key = alipayPublicKey();
        if (key == null || sign == null || !RsaSigner.verify(key, canonical(params), sign)) {
            throw new PayException(PayErrorKind.VALIDATION_FAILED, "支付宝回调验签失败");
        }
        String appId = params.getOrDefault("app_id", "");
        String expectedAppId = properties.getAlipay().getAppId();
        if (appId.isBlank() || expectedAppId == null || !appId.equals(expectedAppId)) {
            throw new PayException(PayErrorKind.VALIDATION_FAILED, "支付宝回调应用标识不匹配");
        }
        String tradeState = params.getOrDefault("trade_status", "");
        Integer amount = params.containsKey("total_amount") ? cents(params.get("total_amount")) : null;
        return new ChannelNotify(
                params.getOrDefault("out_trade_no", ""),
                params.getOrDefault("trade_no", ""),
                isPaidTradeState(tradeState),
                amount,
                tradeState,
                params.getOrDefault("notify_type", ""),
                parseInstant(params.getOrDefault("gmt_payment", "")));
    }

    @Override
    public String successResponseBody() {
        return "success";
    }

    private JsonNode invoke(String method, ObjectNode bizContent) {
        return invoke(method, bizContent, Map.of(), true);
    }

    private JsonNode invoke(
            String method, ObjectNode bizContent, Map<String, String> extraParams, boolean withNotifyUrl) {
        PayChannelProperties.Alipay alipay = properties.getAlipay();
        Map<String, String> params = new TreeMap<>();
        params.put("app_id", alipay.getAppId());
        params.put("method", method);
        params.put("charset", "utf-8");
        params.put("sign_type", alipay.getSignType());
        params.put("timestamp", TIMESTAMP_FORMAT.format(Instant.now()));
        params.put("version", "1.0");
        params.put("format", "JSON");
        if (withNotifyUrl) {
            params.put("notify_url", notifyUrl());
        }
        if (bizContent != null) {
            params.put("biz_content", bizContent.toString());
        }
        params.putAll(extraParams);
        String signature = RsaSigner.signBase64(appPrivateKey(), canonical(params));
        params.put("sign", signature);
        HttpResponse<String> response;
        try {
            response = http.send(
                    "POST",
                    gateway(),
                    Map.of("Content-Type", "application/x-www-form-urlencoded;charset=utf-8"),
                    encodeForm(params),
                    payProperties.getRequestTimeout());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PayException(PayErrorKind.UPSTREAM_FAILED, "支付宝请求被中断", exception);
        } catch (Exception exception) {
            throw new PayException(PayErrorKind.UPSTREAM_FAILED, "支付宝请求失败", exception);
        }
        if (response.statusCode() / 100 != 2) {
            throw new PayException(PayErrorKind.UPSTREAM_FAILED, "支付宝返回 " + response.statusCode());
        }
        return extractBusiness(response.body(), method);
    }

    /**
     * 取出 {@code alipay_<method>_response} 业务节点；非成功码抛出上游失败。
     */
    private JsonNode extractBusiness(String body, String method) {
        JsonNode node;
        try {
            node = objectMapper.readTree(body);
        } catch (Exception exception) {
            throw new PayException(PayErrorKind.UPSTREAM_FAILED, "支付宝返回体解析失败", exception);
        }
        String key = method.replace('.', '_') + "_response";
        JsonNode business = node.path(key);
        String code = business.path("code").asText("");
        if (!SUCCESS_CODE.equals(code)) {
            String message =
                    business.path("sub_msg").asText(business.path("msg").asText("未知错误"));
            throw new PayException(PayErrorKind.UPSTREAM_FAILED, "支付宝业务失败：" + message);
        }
        return business;
    }

    private String notifyUrl() {
        String base = ops.getPublicBaseUrl();
        if (base == null || base.isBlank()) {
            throw new PayException(PayErrorKind.NOT_CONFIGURED, "未配置对外回调基地址（oc.pay.ops.public-base-url）");
        }
        return base.replaceAll("/+$", "") + properties.getAlipay().getNotifyPath();
    }

    /**
     * 支付宝规范串：剔除 sign / sign_type，其余非空参数按 key 升序，k=v 以 & 连接。
     */
    private static String canonical(Map<String, String> params) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : new TreeMap<>(params).entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if ("sign".equals(key) || "sign_type".equals(key) || value == null || value.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('&');
            }
            builder.append(key).append('=').append(value);
        }
        return builder.toString();
    }

    private static String encodeForm(Map<String, String> params) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (builder.length() > 0) {
                builder.append('&');
            }
            builder.append(entry.getKey())
                    .append('=')
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        return builder.toString();
    }

    private static Map<String, String> parseForm(String body) {
        Map<String, String> params = new LinkedHashMap<>();
        if (body == null || body.isBlank()) {
            return params;
        }
        for (String pair : body.split("&")) {
            int index = pair.indexOf('=');
            if (index <= 0) {
                continue;
            }
            String key = URLDecoder.decode(pair.substring(0, index), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(pair.substring(index + 1), StandardCharsets.UTF_8);
            params.put(key, value);
        }
        return params;
    }

    private static String yuan(int cents) {
        return BigDecimal.valueOf(cents, 2)
                .setScale(2, RoundingMode.UNNECESSARY)
                .toPlainString();
    }

    private static int cents(String yuan) {
        try {
            return new BigDecimal(yuan)
                    .movePointRight(2)
                    .setScale(0, RoundingMode.HALF_UP)
                    .intValueExact();
        } catch (Exception exception) {
            return 0;
        }
    }

    private static boolean isPaidTradeState(String tradeState) {
        return "TRADE_SUCCESS".equals(tradeState) || "TRADE_FINISHED".equals(tradeState);
    }

    private void requireAvailable() {
        if (!available()) {
            throw new PayException(PayErrorKind.NOT_CONFIGURED, "支付宝未启用或凭据不全");
        }
    }

    private PrivateKey appPrivateKey() {
        PrivateKey key = appPrivateKey;
        if (key == null) {
            synchronized (this) {
                if (appPrivateKey == null) {
                    PayChannelProperties.Alipay alipay = properties.getAlipay();
                    appPrivateKey = Pem.privateKey(alipay.getPrivateKeyPath(), alipay.getPrivateKeyPem());
                }
                key = appPrivateKey;
            }
        }
        return key;
    }

    private PublicKey alipayPublicKey() {
        PublicKey key = alipayPublicKey;
        if (key == null) {
            synchronized (this) {
                if (alipayPublicKey == null) {
                    PayChannelProperties.Alipay alipay = properties.getAlipay();
                    try {
                        alipayPublicKey =
                                Pem.publicKey(alipay.getAlipayPublicKeyPath(), alipay.getAlipayPublicKeyPem());
                    } catch (Pem.PemException exception) {
                        log.warn("支付宝公钥读取失败：{}", exception.getMessage());
                    }
                }
                key = alipayPublicKey;
            }
        }
        return key;
    }

    private String gateway() {
        String value = properties.getAlipay().getGatewayUrl();
        return value == null || value.isBlank() ? "https://openapi.alipay.com/gateway.do" : value;
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return java.time.LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    .atOffset(ZoneOffset.ofHours(8))
                    .toInstant();
        } catch (Exception exception) {
            return null;
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
