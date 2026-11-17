package com.offercoach.pay.service.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.offercoach.pay.config.PayProperties;
import com.offercoach.pay.service.TestKeys;
import com.offercoach.pay.service.config.PayChannelProperties;
import com.offercoach.pay.service.config.PayOpsProperties;
import com.offercoach.pay.service.crypto.RsaSigner;
import com.offercoach.pay.service.error.PayException;
import java.security.PrivateKey;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WechatPayChannelTest {

    private static final String API_V3_KEY = "01234567890123456789012345678901";
    private static final String NONCE = "123456789012";
    private static final String AAD = "transaction";

    private final ObjectMapper mapper = new ObjectMapper();
    private TestKeys.Generated keys;
    private WechatPayChannel channel;

    @BeforeEach
    void setUp() {
        keys = TestKeys.generate();
        PayChannelProperties properties = new PayChannelProperties();
        PayChannelProperties.Wechat wechat = properties.getWechat();
        wechat.setEnabled(true);
        wechat.setAppId("wx-app");
        wechat.setMchId("mch-1");
        wechat.setMerchantSerialNo("serial-1");
        wechat.setApiV3Key(API_V3_KEY);
        wechat.setPrivateKeyPem(keys.privatePem());
        wechat.setPlatformPublicKeyPem(keys.publicPem());
        channel = new WechatPayChannel(properties, new PayOpsProperties(), mapper, new PayProperties());
    }

    @Test
    void availableWhenConfigured() {
        assertThat(channel.available()).isTrue();
        assertThat(channel.notifyPath()).isEqualTo("/pay/notify/wechat");
    }

    @Test
    void parseNotifyVerifiesAndDecrypts() {
        String body = notifyBody();
        Map<String, String> headers = signedHeaders(body, "1700000000", "nonce-abc");

        PaymentChannel.ChannelNotify notify = channel.parseNotify(headers, body);

        assertThat(notify.paid()).isTrue();
        assertThat(notify.orderNo()).isEqualTo("OC202611170001");
        assertThat(notify.providerTxnId()).isEqualTo("WX-TXN-1");
        assertThat(notify.amountCents()).isEqualTo(3900);
    }

    @Test
    void parseNotifyRejectsBadSignature() {
        String body = notifyBody();
        Map<String, String> headers = signedHeaders(body, "1700000000", "nonce-abc");
        headers.put("Wechatpay-Signature", "invalid-signature");

        assertThatThrownBy(() -> channel.parseNotify(headers, body)).isInstanceOf(PayException.class);
    }

    private String notifyBody() {
        String plain =
                """
                {"out_trade_no":"OC202611170001","transaction_id":"WX-TXN-1","trade_state":"SUCCESS",\
                "amount":{"total":3900},"success_time":"2026-11-17T10:00:00Z"}""";
        String ciphertext = TestKeys.encryptGcm(API_V3_KEY, NONCE, AAD, plain);
        ObjectNode resource = mapper.createObjectNode();
        resource.put("algorithm", "AEAD_AES_256_GCM");
        resource.put("ciphertext", ciphertext);
        resource.put("nonce", NONCE);
        resource.put("associated_data", AAD);
        ObjectNode root = mapper.createObjectNode();
        root.put("event_type", "TRANSACTION.SUCCESS");
        root.set("resource", resource);
        return root.toString();
    }

    private Map<String, String> signedHeaders(String body, String timestamp, String nonce) {
        PrivateKey privateKey = com.offercoach.pay.service.crypto.Pem.privateKey(null, keys.privatePem());
        String signature = RsaSigner.signBase64(privateKey, timestamp + "\n" + nonce + "\n" + body + "\n");
        Map<String, String> headers = new HashMap<>();
        headers.put("Wechatpay-Timestamp", timestamp);
        headers.put("Wechatpay-Nonce", nonce);
        headers.put("Wechatpay-Signature", signature);
        return headers;
    }
}
