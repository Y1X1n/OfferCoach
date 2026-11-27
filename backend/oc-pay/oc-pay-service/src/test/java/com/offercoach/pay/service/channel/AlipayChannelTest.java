package com.offercoach.pay.service.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.offercoach.pay.config.PayProperties;
import com.offercoach.pay.service.TestKeys;
import com.offercoach.pay.service.config.PayChannelProperties;
import com.offercoach.pay.service.config.PayOpsProperties;
import com.offercoach.pay.service.crypto.Pem;
import com.offercoach.pay.service.crypto.RsaSigner;
import com.offercoach.pay.service.domain.ChannelCode;
import com.offercoach.pay.service.domain.OrderStatus;
import com.offercoach.pay.service.domain.PaymentOrder;
import com.offercoach.pay.service.error.PayException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

    @Test
    void miniPrepayExchangesAuthCodeAndCreatesTrade() throws Exception {
        AtomicReference<String> oauthBody = new AtomicReference<>();
        AtomicReference<String> createBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/gateway.do", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> params = form(body);
            String response;
            if ("alipay.system.oauth.token".equals(params.get("method"))) {
                oauthBody.set(body);
                response = "{\"alipay_system_oauth_token_response\":{\"code\":\"10000\","
                        + "\"user_id\":\"2088000000000001\"}}";
            } else {
                createBody.set(body);
                response = "{\"alipay_trade_create_response\":{\"code\":\"10000\",\"trade_no\":\"2026111722001\"}}";
            }
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            PayChannelProperties properties = new PayChannelProperties();
            PayChannelProperties.Alipay alipay = properties.getAlipay();
            alipay.setEnabled(true);
            alipay.setAppId("2021000000000000");
            alipay.setPrivateKeyPem(keys.privatePem());
            alipay.setGatewayUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/gateway.do");
            PayOpsProperties ops = new PayOpsProperties();
            ops.setPublicBaseUrl("https://pay.example.com");
            AlipayChannel miniChannel = new AlipayChannel(properties, ops, mapper, new PayProperties());

            String tradeNo = miniChannel.miniPrepay(miniOrder(), "auth-code-1");

            assertThat(tradeNo).isEqualTo("2026111722001");
            Map<String, String> oauth = form(oauthBody.get());
            assertThat(oauth.get("grant_type")).isEqualTo("authorization_code");
            assertThat(oauth.get("code")).isEqualTo("auth-code-1");
            assertThat(oauth).doesNotContainKey("notify_url");
            Map<String, String> create = form(createBody.get());
            assertThat(create.get("method")).isEqualTo("alipay.trade.create");
            assertThat(create.get("biz_content")).contains("2088000000000001").contains("OC202611170099");
            assertThat(RsaSigner.verify(keys.publicKey(), canonical(create), create.get("sign")))
                    .isTrue();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void miniPrepayRequiresAuthCode() {
        assertThatThrownBy(() -> channel.miniPrepay(miniOrder(), " "))
                .isInstanceOf(PayException.class)
                .hasMessageContaining("授权码");
    }

    private static PaymentOrder miniOrder() {
        java.time.Instant now = java.time.Instant.now();
        return new PaymentOrder(
                1L,
                "OC202611170099",
                1L,
                "PRO",
                "专业版",
                3900,
                "CNY",
                ChannelCode.ALIPAY,
                OrderStatus.CREATED,
                null,
                null,
                null,
                "key-1",
                "PENDING",
                0,
                now.plusSeconds(1800),
                null,
                null,
                0,
                now,
                now);
    }

    private static Map<String, String> form(String body) {
        Map<String, String> params = new LinkedHashMap<>();
        for (String pair : body.split("&")) {
            int index = pair.indexOf('=');
            if (index <= 0) {
                continue;
            }
            params.put(
                    URLDecoder.decode(pair.substring(0, index), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(index + 1), StandardCharsets.UTF_8));
        }
        return params;
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
