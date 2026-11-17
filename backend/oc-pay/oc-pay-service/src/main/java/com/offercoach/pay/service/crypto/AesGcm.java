package com.offercoach.pay.service.crypto;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM 解密；用于微信支付 v3 回调 resource 密文。
 *
 * <p>密钥为 APIv3 密钥（32 字节），nonce 为 12 字节 ASCII，associated_data 为附加认证数据。</p>
 */
public final class AesGcm {

    private static final int TAG_BITS = 128;
    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private AesGcm() {}

    /**
     * 解密 Base64 密文并返回明文（UTF-8）。
     */
    public static String decryptToString(String key, String nonce, String associatedData, String ciphertextBase64) {
        if (key == null || key.isBlank() || ciphertextBase64 == null || ciphertextBase64.isBlank()) {
            throw new Pem.PemException("回调密文或密钥缺失");
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
            GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_BITS, nonce.getBytes(StandardCharsets.UTF_8));
            cipher.init(Cipher.DECRYPT_MODE, keySpec, parameterSpec);
            if (associatedData != null && !associatedData.isEmpty()) {
                cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
            }
            byte[] plain = cipher.doFinal(java.util.Base64.getDecoder().decode(ciphertextBase64));
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new Pem.PemException("回调密文解密失败", exception);
        }
    }

    /**
     * 生成随机 nonce 串，用于签名请求头。
     */
    public static String nonce(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            builder.append(ALPHANUMERIC.charAt(RANDOM.nextInt(ALPHANUMERIC.length())));
        }
        return builder.toString();
    }
}
