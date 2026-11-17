package com.offercoach.pay.service.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offercoach.pay.config.PayProperties;
import com.offercoach.pay.service.TestKeys;
import com.offercoach.pay.service.config.PayChannelProperties;
import com.offercoach.pay.service.config.PayOpsProperties;
import com.offercoach.pay.service.crypto.Pem;
import com.offercoach.pay.service.crypto.RsaSigner;
import com.offercoach.pay.service.error.PayException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
