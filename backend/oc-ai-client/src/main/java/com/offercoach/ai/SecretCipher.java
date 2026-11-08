package com.offercoach.ai;

import com.offercoach.common.error.BizException;
import com.offercoach.common.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * API Key 的 AES-GCM 加解密；根密钥由 oc.ai.secret 经 SHA-256 派生，密文格式 v1:base64(iv||ciphertext)。
 */
@Component
public class SecretCipher {

    private static final Logger log = LoggerFactory.getLogger(SecretCipher.class);
    private static final String PREFIX = "v1:";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final byte[] key;

    public SecretCipher(AiProperties properties) {
        String secret = properties.getSecret();
        this.key = secret == null || secret.isBlank() ? null : sha256(secret);
    }

    /**
     * 加密 API Key；未配置根密钥时拒绝保存，避免回退到不安全存储。
     */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            throw fieldError("apiKey", "API Key 不能为空");
        }
        if (key == null) {
            throw fieldError("secret", "未配置 OC_AI_SECRET，无法加密保存 API Key");
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException exception) {
            throw fieldError("apiKey", "API Key 加密失败");
        }
    }

    /**
     * 解密 API Key；密文损坏、根密钥变更或未配置时只告警并返回空串，不影响应用启动。
     */
    public String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank()) {
            return "";
        }
        if (key == null) {
            log.warn("AI Key 解密失败：未配置 OC_AI_SECRET");
            return "";
        }
        if (!ciphertext.startsWith(PREFIX)) {
            log.warn("AI Key 解密失败：密文格式不受支持");
            return "";
        }
        try {
            byte[] payload = Base64.getDecoder().decode(ciphertext.substring(PREFIX.length()));
            if (payload.length <= IV_LENGTH) {
                log.warn("AI Key 解密失败：密文长度不足");
                return "";
            }
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_LENGTH_BITS, payload, 0, IV_LENGTH));
            byte[] plain = cipher.doFinal(payload, IV_LENGTH, payload.length - IV_LENGTH);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            log.warn("AI Key 解密失败：reason={}", exception.getClass().getSimpleName());
            return "";
        }
    }

    /**
     * 只保留末 4 位用于管理端展示；过短的 Key 完全遮蔽。
     */
    public static String mask(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }
        if (apiKey.length() <= 4) {
            return "****";
        }
        return "****" + apiKey.substring(apiKey.length() - 4);
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    private static BizException fieldError(String field, String message) {
        return new BizException(ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of(field, message)));
    }
}
