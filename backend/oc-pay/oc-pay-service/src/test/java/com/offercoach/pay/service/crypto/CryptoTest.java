package com.offercoach.pay.service.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import com.offercoach.pay.service.TestKeys;
import java.security.PrivateKey;
import java.security.PublicKey;
import org.junit.jupiter.api.Test;

class CryptoTest {

    @Test
    void pemRoundTripLoadsKeys() {
        TestKeys.Generated generated = TestKeys.generate();
        PrivateKey privateKey = Pem.privateKey(null, generated.privatePem());
        PublicKey publicKey = Pem.publicKey(null, generated.publicPem());

        assertThat(privateKey.getAlgorithm()).isEqualTo("RSA");
        assertThat(publicKey.getAlgorithm()).isEqualTo("RSA");
    }

    @Test
    void signAndVerifySucceed() {
        TestKeys.Generated generated = TestKeys.generate();
        PrivateKey privateKey = Pem.privateKey(null, generated.privatePem());
        PublicKey publicKey = Pem.publicKey(null, generated.publicPem());

        String message = "POST\n/v3/pay/transactions/native\n1700000000\nnonce\n{}\n";
        String signature = RsaSigner.signBase64(privateKey, message);

        assertThat(RsaSigner.verify(publicKey, message, signature)).isTrue();
        assertThat(RsaSigner.verify(publicKey, message + "tampered", signature)).isFalse();
    }

    @Test
    void aesGcmDecryptsCiphertext() {
        String key = "01234567890123456789012345678901";
        String nonce = "123456789012";
        String aad = "transaction";
        String plain = "{\"out_trade_no\":\"OC1\"}";
        String ciphertext = TestKeys.encryptGcm(key, nonce, aad, plain);

        assertThat(AesGcm.decryptToString(key, nonce, aad, ciphertext)).isEqualTo(plain);
    }

    @Test
    void pemRejectsUnsupportedFormat() {
        try {
            Pem.privateKey(null, "not-a-pem");
        } catch (Pem.PemException exception) {
            assertThat(exception.getMessage()).contains("不受支持");
            return;
        }
        throw new AssertionError("应抛出 PemException");
    }
}
