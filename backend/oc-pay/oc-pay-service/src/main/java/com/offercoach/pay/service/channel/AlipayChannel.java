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
 * <p>请求以应用私钥做 RSA2 签名；回调与同步响应都以支付宝公钥验签（同步响应验的是
 * 业务节点的原始字节，见 {@link #rawBusinessNode}）。商户私钥与支付宝公钥都来自部署配置，
 * 缺任何一个渠道都不算可用。</p>
 */
@Component
public class AlipayChannel implements PaymentChannel {

    private static final Logger log = LoggerFactory.getLogger(AlipayChannel.class);
    private static final String SUCCESS_CODE = "10000";
    private static final String PRECREATE_METHOD = "alipay.trade.precreate";
    private static final String QUERY_METHOD = "alipay.trade.query";
    private static final String RSA2_SIGN_TYPE = "RSA2";
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
                && (hasText(alipay.getPrivateKeyPath()) || hasText(alipay.getPrivateKeyPem()))
                // 没有平台公钥就无法验证回调与同步响应，这种配置只会在下单/对账时才炸，
                // 不如在能力判定这一层就判为不可用（OrderService 会据此直接拒绝下单）。
                && (hasText(alipay.getAlipayPublicKeyPath()) || hasText(alipay.getAlipayPublicKeyPem()));
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
        PayChannelProperties.Alipay alipay = properties.getAlipay();
        Map<String, String> params = new TreeMap<>();
        params.put("app_id", alipay.getAppId());
        params.put("method", method);
        params.put("charset", "utf-8");
        params.put("sign_type", alipay.getSignType());
        params.put("timestamp", TIMESTAMP_FORMAT.format(Instant.now()));
        params.put("version", "1.0");
        params.put("format", "JSON");
        params.put("notify_url", notifyUrl());
        params.put("biz_content", bizContent.toString());
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
        return parseResponse(response.body(), method);
    }

    /**
     * 校验并取出 {@code alipay_<method>_response} 业务节点；非成功码抛出上游失败。
     *
     * <p>同步响应同样必须验签：HTTPS 只保护传输，如果网关地址被改错、DNS 或代理被劫持、
     * 或者有人把 gateway 指向一个自建服务，不验签就等于完全信任对端——一条伪造的
     * "退款成功/已支付"响应足以让订单状态与资金事实脱节。</p>
     *
     * <p>签名覆盖的是**响应里业务节点的原始字节**：这里不做任何重新序列化，直接从原始
     * 返回体里按括号配对切出子串再验，避免"重新格式化后签名对不上"。</p>
     */
    JsonNode parseResponse(String body, String method) {
        JsonNode node;
        try {
            node = objectMapper.readTree(body);
        } catch (Exception exception) {
            throw new PayException(PayErrorKind.UPSTREAM_FAILED, "支付宝返回体解析失败", exception);
        }
        String key = method.replace('.', '_') + "_response";
        JsonNode business = node.path(key);
        if (!business.isObject()) {
            throw new PayException(PayErrorKind.UPSTREAM_FAILED, "支付宝响应缺少业务节点：" + key);
        }
        verifyResponseSignature(body, key, node);
        String code = business.path("code").asText("");
        if (!SUCCESS_CODE.equals(code)) {
            String message =
                    business.path("sub_msg").asText(business.path("msg").asText("未知错误"));
            throw new PayException(PayErrorKind.UPSTREAM_FAILED, "支付宝业务失败：" + message);
        }
        return business;
    }

    private void verifyResponseSignature(String body, String responseKey, JsonNode root) {
        PublicKey key = alipayPublicKey();
        if (key == null) {
            // 没有平台公钥就无法验证响应来源；这与回调验签是同一个前提，不能静默放行。
            throw new PayException(PayErrorKind.NOT_CONFIGURED, "未配置支付宝公钥，无法验证响应签名");
        }
        String signType = root.path("sign_type").asText("");
        if (!signType.isBlank() && !RSA2_SIGN_TYPE.equalsIgnoreCase(signType)) {
            throw new PayException(PayErrorKind.UPSTREAM_FAILED, "支付宝响应签名类型不支持：" + signType);
        }
        String sign = root.path("sign").asText("");
        String signed = rawBusinessNode(body, responseKey);
        if (sign.isBlank() || signed == null || !RsaSigner.verify(key, signed, sign)) {
            throw new PayException(PayErrorKind.UPSTREAM_FAILED, "支付宝响应验签失败");
        }
    }

    /**
     * 从原始返回体里切出**根对象上的**业务节点子串（含括号），供验签使用。
     *
     * <p>为什么不能重新序列化：签名覆盖的是支付宝下发的原始字节，字段顺序、空白、数字格式
     * 一变签名就对不上。</p>
     *
     * <p>为什么不能简单 {@code indexOf}：那样会被"先塞一个签过名的旧节点、再放一个伪造的
     * 同名根字段"绕过——{@code indexOf} 取到前面那个（验签通过），而 Jackson 取根字段时
     * 重复键是后者优先，最终用的是伪造内容。所以这里只在根层（depth=1）匹配键，并且出现
     * 两次同名根键就直接判为不可信。</p>
     */
    private static String rawBusinessNode(String body, String responseKey) {
        String found = null;
        String pendingKey = null;
        int depth = 0;
        int index = 0;
        while (index < body.length()) {
            char current = body.charAt(index);
            if (current == '"') {
                int end = stringEnd(body, index);
                if (end < 0) {
                    return null;
                }
                if (depth == 1) {
                    pendingKey = body.substring(index + 1, end);
                }
                index = end + 1;
                continue;
            }
            if (current == '{' || current == '[') {
                depth++;
                if (depth == 2 && responseKey.equals(pendingKey)) {
                    int close = matchingClose(body, index);
                    if (close < 0 || found != null) {
                        // 括号不配对，或根层出现两个同名业务节点：不可信，直接失败
                        return null;
                    }
                    found = body.substring(index, close + 1);
                }
                pendingKey = null;
                index++;
                continue;
            }
            if (current == '}' || current == ']') {
                depth--;
                pendingKey = null;
                index++;
                continue;
            }
            index++;
        }
        return found;
    }

    /** 从引号位置找到配对的收尾引号（跳过转义）；找不到返回 -1。 */
    private static int stringEnd(String body, int quoteAt) {
        boolean escaped = false;
        for (int index = quoteAt + 1; index < body.length(); index++) {
            char current = body.charAt(index);
            if (escaped) {
                escaped = false;
            } else if (current == '\\') {
                escaped = true;
            } else if (current == '"') {
                return index;
            }
        }
        return -1;
    }

    /** 从开括号位置找到配对的收括号（跳过字符串内的括号）；找不到返回 -1。 */
    private static int matchingClose(String body, int openAt) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int index = openAt; index < body.length(); index++) {
            char current = body.charAt(index);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }
            if (current == '"') {
                inString = true;
            } else if (current == '{' || current == '[') {
                depth++;
            } else if (current == '}' || current == ']') {
                depth--;
                if (depth == 0) {
                    return index;
                }
            }
        }
        return -1;
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
