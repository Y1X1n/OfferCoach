package com.offercoach.pay.service.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.offercoach.pay.config.PayProperties;
import com.offercoach.pay.service.TestKeys;
import com.offercoach.pay.service.config.PayChannelProperties;
import com.offercoach.pay.service.config.PayOpsProperties;
import com.offercoach.pay.service.crypto.Pem;
import com.offercoach.pay.service.crypto.RsaSigner;
import com.offercoach.pay.service.error.PayErrorKind;
import com.offercoach.pay.service.error.PayException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class AlipayChannelTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private TestKeys.Generated keys;
    private AlipayChannel channel;

    @BeforeEach
    void setUp() {
        keys = TestKeys.generate();
        PayChannelProperties properties = new PayChannelProperties();
        PayChannelProperties.Alipay alipay = properties.getAlipay();
        alipay.setEnabled(true);
        alipay.setAppId("2021000000000000");
        alipay.setPrivateKeyPem(keys.privatePem());
        alipay.setAlipayPublicKeyPem(keys.publicPem());
        channel = new AlipayChannel(properties, new PayOpsProperties(), mapper, new PayProperties());
    }

    @Test
    void availableWhenConfigured() {
        assertThat(channel.available()).isTrue();
        assertThat(channel.notifyPath()).isEqualTo("/pay/notify/alipay");
    }

    @Test
    void parseNotifyVerifiesAndNormalizes() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("app_id", "2021000000000000");
        params.put("notify_type", "trade_status_sync");
        params.put("out_trade_no", "OC202611170002");
        params.put("trade_no", "ALI-TXN-1");
        params.put("trade_status", "TRADE_SUCCESS");
        params.put("total_amount", "39.00");
        params.put("sign_type", "RSA2");
        params.put("gmt_payment", "2026-11-17 10:00:00");
        String body = signedForm(params);

        PaymentChannel.ChannelNotify notify = channel.parseNotify(Map.of(), body);

        assertThat(notify.paid()).isTrue();
        assertThat(notify.orderNo()).isEqualTo("OC202611170002");
        assertThat(notify.providerTxnId()).isEqualTo("ALI-TXN-1");
        assertThat(notify.amountCents()).isEqualTo(3900);
    }

    @Test
    void parseNotifyRejectsBadSignature() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("out_trade_no", "OC3");
        params.put("trade_status", "TRADE_SUCCESS");
        params.put("total_amount", "1.00");
        params.put("sign_type", "RSA2");
        String body = signedForm(params) + "&sign=" + URLEncoder.encode("broken", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> channel.parseNotify(Map.of(), body)).isInstanceOf(PayException.class);
    }

    // ---- 同步响应验签 ----

    @Test
    void signedResponseIsAccepted() {
        String business =
                "{\"code\":\"10000\",\"msg\":\"Success\",\"trade_status\":\"TRADE_SUCCESS\",\"total_amount\":\"19.99\"}";

        JsonNode node = channel.parseResponse(response(business, "RSA2"), "alipay.trade.query");

        assertThat(node.path("trade_status").asText()).isEqualTo("TRADE_SUCCESS");
        assertThat(node.path("total_amount").asText()).isEqualTo("19.99");
    }

    /** 签名对得上但内容被改过：必须拒绝，否则一条伪造的"已支付"响应就能改订单状态。 */
    @Test
    void tamperedResponseIsRejected() {
        String business = "{\"code\":\"10000\",\"trade_status\":\"TRADE_SUCCESS\"}";
        String body = response(business, "RSA2").replace("TRADE_SUCCESS", "TRADE_FINISHED");

        assertThatThrownBy(() -> channel.parseResponse(body, "alipay.trade.query"))
                .isInstanceOf(PayException.class)
                .satisfies(exception ->
                        assertThat(((PayException) exception).kind()).isEqualTo(PayErrorKind.UPSTREAM_FAILED));
    }

    @Test
    void unsignedResponseIsRejected() {
        String business = "{\"code\":\"10000\",\"trade_status\":\"TRADE_SUCCESS\"}";
        String body = "{\"alipay_trade_query_response\":" + business + "}";

        assertThatThrownBy(() -> channel.parseResponse(body, "alipay.trade.query"))
                .isInstanceOf(PayException.class);
    }

    @Test
    void nonRsa2SignatureTypeIsRejected() {
        String business = "{\"code\":\"10000\",\"trade_status\":\"TRADE_SUCCESS\"}";

        assertThatThrownBy(() -> channel.parseResponse(response(business, "RSA"), "alipay.trade.query"))
                .isInstanceOf(PayException.class);
    }

    @Test
    void missingPublicKeyRejectsResponse() {
        PayChannelProperties properties = new PayChannelProperties();
        PayChannelProperties.Alipay alipay = properties.getAlipay();
        alipay.setEnabled(true);
        alipay.setAppId("2021000000000000");
        alipay.setPrivateKeyPem(keys.privatePem());
        alipay.setAlipayPublicKeyPem(null);
        AlipayChannel withoutKey = new AlipayChannel(properties, new PayOpsProperties(), mapper, new PayProperties());
        String business = "{\"code\":\"10000\",\"trade_status\":\"TRADE_SUCCESS\"}";

        assertThatThrownBy(() -> withoutKey.parseResponse(response(business, "RSA2"), "alipay.trade.query"))
                .isInstanceOf(PayException.class)
                .satisfies(exception ->
                        assertThat(((PayException) exception).kind()).isEqualTo(PayErrorKind.NOT_CONFIGURED));
    }

    /** 字符串里出现**不配对**的花括号与转义引号时，切子串不能提前收尾。 */
    @Test
    void bracesInsideJsonStringsDoNotBreakExtraction() {
        String business =
                "{\"code\":\"10000\",\"sub_msg\":\"备注 } 右括号 { 左括号 与 \\\"引号\\\"\",\"trade_status\":\"TRADE_SUCCESS\"}";

        JsonNode node = channel.parseResponse(response(business, "RSA2"), "alipay.trade.query");

        assertThat(node.path("trade_status").asText()).isEqualTo("TRADE_SUCCESS");
    }

    /**
     * 根层出现两个同名业务节点：Jackson 取后者、`indexOf` 取前者，组合起来就能"用一个
     * 签过名的旧节点过验签、让伪造内容被解析"。这种响应一律判为不可信。
     */
    @Test
    void duplicateRootBusinessNodeIsRejected() {
        String captured = "{\"code\":\"10000\",\"trade_status\":\"TRADE_SUCCESS\"}";
        String forged = "{\"code\":\"10000\",\"trade_status\":\"TRADE_FINISHED\"}";
        String body = "{\"alipay_trade_query_response\":" + captured + ",\"alipay_trade_query_response\":" + forged
                + ",\"sign\":\"" + sign(captured) + "\"}";

        assertThatThrownBy(() -> channel.parseResponse(body, "alipay.trade.query"))
                .isInstanceOf(PayException.class);
    }

    /**
     * 签过名的节点藏在别的根字段里、真正的根字段是伪造的：不能因为"体里能找到一段能过验签
     * 的字节"就放行。
     */
    @Test
    void signedNodeNestedOutsideRootIsNotAccepted() {
        String captured = "{\"code\":\"10000\",\"trade_status\":\"TRADE_SUCCESS\"}";
        String forged = "{\"code\":\"10000\",\"trade_status\":\"TRADE_FINISHED\"}";
        String body = "{\"junk\":{\"alipay_trade_query_response\":" + captured + "},\"alipay_trade_query_response\":"
                + forged + ",\"sign\":\"" + sign(captured) + "\"}";

        assertThatThrownBy(() -> channel.parseResponse(body, "alipay.trade.query"))
                .isInstanceOf(PayException.class);
    }

    /** 按支付宝下发的原始字节签名：重复空白与字段顺序都不影响验签。 */
    private String response(String business, String signType) {
        String sign = sign(business);
        return "{\"alipay_trade_query_response\": " + business + ",\"sign\":\"" + sign + "\",\"sign_type\":\""
                + signType + "\"}";
    }

    private String sign(String message) {
        return RsaSigner.signBase64(keys.privateKey(), message);
    }

    private String signedForm(Map<String, String> params) {
        PrivateKey privateKey = Pem.privateKey(null, keys.privatePem());
        String sign = RsaSigner.signBase64(privateKey, canonical(params));
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            appendParam(builder, entry.getKey(), entry.getValue());
        }
        appendParam(builder, "sign", sign);
        return builder.toString();
    }

    private static void appendParam(StringBuilder builder, String key, String value) {
        if (builder.length() > 0) {
            builder.append('&');
        }
        builder.append(key).append('=').append(URLEncoder.encode(value, StandardCharsets.UTF_8));
    }

    private static String canonical(Map<String, String> params) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : new TreeMap<>(params).entrySet()) {
            if ("sign".equals(entry.getKey())
                    || "sign_type".equals(entry.getKey())
                    || entry.getValue().isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('&');
            }
            builder.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return builder.toString();
    }
}
