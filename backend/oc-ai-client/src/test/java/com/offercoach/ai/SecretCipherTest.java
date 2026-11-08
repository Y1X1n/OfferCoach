package com.offercoach.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offercoach.common.error.BizException;
import com.offercoach.common.error.ErrorCode;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/**
 * AES-GCM 加解密、篡改识别与掩码规则。
 */
class SecretCipherTest {

    private static final String PREFIX = "v1:";

    @Test
    void encryptsAndDecryptsRoundTrip() {
        SecretCipher cipher = cipher("unit-test-secret");
        String encrypted = cipher.encrypt("sk-offercoach-1234567890");
        assertTrue(encrypted.startsWith(PREFIX));
        assertEquals("sk-offercoach-1234567890", cipher.decrypt(encrypted));
    }

    @Test
    void usesRandomIvForSamePlaintext() {
        SecretCipher cipher = cipher("unit-test-secret");
        assertNotEquals(cipher.encrypt("same-key"), cipher.encrypt("same-key"));
    }

    @Test
    void returnsEmptyWhenCiphertextIsTampered() {
        SecretCipher cipher = cipher("unit-test-secret");
        String encrypted = cipher.encrypt("sk-tamper-check");
        byte[] payload = Base64.getDecoder().decode(encrypted.substring(PREFIX.length()));
        payload[payload.length - 1] ^= 0x01;
        String tampered = PREFIX + Base64.getEncoder().encodeToString(payload);
        assertEquals("", cipher.decrypt(tampered));
    }

    @Test
    void returnsEmptyWhenSecretChanged() {
        String encrypted = cipher("first-secret").encrypt("sk-cross-secret");
        assertEquals("", cipher("second-secret").decrypt(encrypted));
    }

    @Test
    void returnsEmptyForMalformedCiphertext() {
        SecretCipher cipher = cipher("unit-test-secret");
        assertEquals("", cipher.decrypt(null));
        assertEquals("", cipher.decrypt(""));
        assertEquals("", cipher.decrypt("plain-text"));
        assertEquals("", cipher.decrypt("v2:abcd"));
        assertEquals("", cipher.decrypt("v1:not-base64!!"));
        assertEquals("", cipher.decrypt("v1:AAAA"));
    }

    @Test
    void rejectsSavingKeyWithoutRootSecret() {
        SecretCipher cipher = cipher("");
        BizException exception = assertThrows(BizException.class, () -> cipher.encrypt("sk-no-secret"));
        assertEquals(ErrorCode.VALIDATION_FAILED, exception.errorCode());
        assertEquals("", cipher.decrypt("v1:AAAA"));
    }

    @Test
    void masksAllButLastFourCharacters() {
        assertEquals("****7890", SecretCipher.mask("sk-offercoach-1234567890"));
        assertEquals("****", SecretCipher.mask("abc"));
        assertNull(SecretCipher.mask(null));
        assertNull(SecretCipher.mask(""));
    }

    private static SecretCipher cipher(String secret) {
        AiProperties properties = new AiProperties();
        properties.setSecret(secret);
        return new SecretCipher(properties);
    }
}
