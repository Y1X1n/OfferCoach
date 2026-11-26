package com.offercoach.pay.service;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 测试辅助：生成 RSA 密钥对、PEM 编码与 AES-GCM 加密，用于渠道签名/验签与回调解密往返验证。
 */
public final class TestKeys {

    /** 渠道签名固定 RSA；集中为常量，避免散落的算法字面量。 */
    private static final String KEY_ALGORITHM = "RSA";
    private static final int KEY_SIZE = 4096;

    private TestKeys() {}

    public static Generated generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(KEY_ALGORITHM);
            generator.initialize(KEY_SIZE);
            KeyPair pair = generator.generateKeyPair();
            return new Generated(
                    pair.getPrivate(),
                    pair.getPublic(),
                    pem("PRIVATE KEY", pair.getPrivate().getEncoded()),
                    pem("PUBLIC KEY", pair.getPublic().getEncoded()));
        } catch (Exception exception) {
            throw new IllegalStateException("生成测试密钥失败", exception);
        }
    }

    public static String pem(String type, byte[] der) {
        String base64 = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(der);
        return "-----BEGIN " + type + "-----\n" + base64 + "\n-----END " + type + "-----\n";
    }

    public static String encryptGcm(String key, String nonce, String aad, String plain) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
            cipher.init(
                    Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(128, nonce.getBytes(StandardCharsets.UTF_8)));
            cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception exception) {
            throw new IllegalStateException("测试加密失败", exception);
        }
    }

    public record Generated(PrivateKey privateKey, PublicKey publicKey, String privatePem, String publicPem) {}
}
