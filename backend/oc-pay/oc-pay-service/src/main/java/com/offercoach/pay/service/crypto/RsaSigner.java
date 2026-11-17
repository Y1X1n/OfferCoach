package com.offercoach.pay.service.crypto;

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;

/**
 * RSA 签名与验签；SHA256withRSA（PKCS#1 v1.5）。
 *
 * <p>微信支付 v3 的请求签名与回调验签、支付宝 RSA2 均使用该算法。
 * 签名与验签输入均为原始字节，调用方负责拼接规范串。</p>
 */
public final class RsaSigner {

    private static final String ALGORITHM = "SHA256withRSA";

    private RsaSigner() {}

    /**
     * 计算签名并返回 Base64 字符串。
     */
    public static String signBase64(PrivateKey privateKey, String message) {
        try {
            Signature signature = Signature.getInstance(ALGORITHM);
            signature.initSign(privateKey);
            signature.update(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (Exception exception) {
            throw new Pem.PemException("签名失败", exception);
        }
    }

    /**
     * 验签；签名非法或密钥不匹配返回 false，不抛异常。
     */
    public static boolean verify(PublicKey publicKey, String message, String base64Signature) {
        if (base64Signature == null || base64Signature.isBlank()) {
            return false;
        }
        try {
            Signature signature = Signature.getInstance(ALGORITHM);
            signature.initVerify(publicKey);
            signature.update(message.getBytes(StandardCharsets.UTF_8));
            return signature.verify(Base64.getDecoder().decode(base64Signature));
        } catch (Exception exception) {
            return false;
        }
    }
}
