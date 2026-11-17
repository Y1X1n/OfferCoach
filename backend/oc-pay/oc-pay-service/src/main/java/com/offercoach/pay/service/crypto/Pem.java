package com.offercoach.pay.service.crypto;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * PEM 密钥读取：商户私钥（PKCS#8）、渠道公钥（X.509 SubjectPublicKeyInfo）与证书公钥。
 *
 * <p>仅支持 PKCS#8（{@code BEGIN PRIVATE KEY}）。微信支付商户私钥与支付宝 Java 侧私钥均为 PKCS#8；
 * 若为 PKCS#1（{@code BEGIN RSA PRIVATE KEY}），需先转换为 PKCS#8。</p>
 */
public final class Pem {

    private static final String PRIVATE_HEADER = "-----BEGIN PRIVATE KEY-----";
    private static final String PUBLIC_HEADER = "-----BEGIN PUBLIC KEY-----";
    private static final String CERT_HEADER = "-----BEGIN CERTIFICATE-----";

    private Pem() {}

    /**
     * 读取 PKCS#8 私钥；path 与 inline 至少一个非空，path 优先。
     */
    public static PrivateKey privateKey(String path, String inline) {
        String content = read(path, inline);
        requireHeader(content, PRIVATE_HEADER, "私钥");
        byte[] der = decodeBody(content, PRIVATE_HEADER, "-----END PRIVATE KEY-----");
        try {
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception exception) {
            throw new PemException("私钥解析失败", exception);
        }
    }

    /**
     * 读取 X.509 公钥；path 与 inline 至少一个非空，path 优先。
     */
    public static PublicKey publicKey(String path, String inline) {
        String content = read(path, inline);
        requireHeader(content, PUBLIC_HEADER, "公钥");
        byte[] der = decodeBody(content, PUBLIC_HEADER, "-----END PUBLIC KEY-----");
        try {
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception exception) {
            throw new PemException("公钥解析失败", exception);
        }
    }

    /**
     * 读取 X.509 证书并返回其公钥；微信平台证书、支付宝公钥证书均适用。
     */
    public static PublicKey certificatePublicKey(String path, String inline) {
        String content = read(path, inline);
        requireHeader(content, CERT_HEADER, "证书");
        try (ByteArrayInputStream stream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))) {
            X509Certificate certificate =
                    (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(stream);
            return certificate.getPublicKey();
        } catch (Exception exception) {
            throw new PemException("证书解析失败", exception);
        }
    }

    private static String read(String path, String inline) {
        if (path != null && !path.isBlank()) {
            try {
                return Files.readString(Path.of(path.trim()), StandardCharsets.UTF_8);
            } catch (Exception exception) {
                throw new PemException("密钥文件读取失败：" + path, exception);
            }
        }
        if (inline != null && !inline.isBlank()) {
            return inline;
        }
        throw new PemException("密钥未配置");
    }

    private static void requireHeader(String content, String header, String label) {
        if (!content.contains(header)) {
            throw new PemException(label + "格式不受支持（缺少 " + header + "）");
        }
    }

    private static byte[] decodeBody(String content, String header, String footer) {
        String body = content.replace(header, "").replace(footer, "").replaceAll("\\s", "");
        try {
            return Base64.getDecoder().decode(body);
        } catch (IllegalArgumentException exception) {
            throw new PemException("密钥 Base64 解码失败", exception);
        }
    }

    /**
     * 密钥读取失败；由调用方按渠道未配置处理。
     */
    public static class PemException extends RuntimeException {
        public PemException(String message) {
            super(message);
        }

        public PemException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
