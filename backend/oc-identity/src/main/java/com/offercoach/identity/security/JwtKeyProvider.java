package com.offercoach.identity.security;

import com.offercoach.identity.config.AuthProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 提供 RS256 签名所需的 RSA 密钥。
 *
 * <p>配置了 PEM 路径时从文件加载 PKCS#8 私钥 / X.509 公钥；未配置时仅在非 prod
 * profile 下生成临时 RSA 2048 密钥对并在日志中告警，仅适合本地开发（重启后旧 Token
 * 全部失效）。prod profile 缺密钥时启动失败，避免多副本互不认 Token。</p>
 */
@Component
public class JwtKeyProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtKeyProvider.class);
    private static final String DEFAULT_KEY_ID = "oc-local";
    private static final String PROD_PROFILE = "prod";
    private static final int KEY_SIZE = 2048;

    private final RSAPublicKey publicKey;
    private final RSAPrivateKey privateKey;
    private final String keyId;

    public JwtKeyProvider(AuthProperties properties, Environment environment) {
        AuthProperties.Jwt jwt = properties.getJwt();
        this.keyId = hasText(jwt.getKeyId()) ? jwt.getKeyId().trim() : DEFAULT_KEY_ID;

        String privateKeyPath = trimToNull(jwt.getPrivateKeyPath());
        String publicKeyPath = trimToNull(jwt.getPublicKeyPath());

        if (privateKeyPath == null && publicKeyPath == null) {
            if (isProductionProfile(environment)) {
                throw new IllegalStateException("prod profile 必须配置 oc.auth.jwt.private-key-path 与"
                        + " public-key-path，禁止生成临时 RSA 密钥（会导致重启后 Token 全部失效）");
            }
            KeyPair keyPair = generateKeyPair();
            this.publicKey = (RSAPublicKey) keyPair.getPublic();
            this.privateKey = (RSAPrivateKey) keyPair.getPrivate();
            log.warn(
                    "oc.auth.jwt.private-key-path/public-key-path 未配置，已生成临时 RSA-2048 密钥对"
                            + "（kid={}），重启后 Token 全部失效，仅限本地开发",
                    keyId);
            return;
        }
        if (privateKeyPath == null || publicKeyPath == null) {
            throw new IllegalStateException("oc.auth.jwt.private-key-path 与 public-key-path 必须同时配置或同时留空");
        }
        this.privateKey = readPrivateKey(privateKeyPath);
        this.publicKey = readPublicKey(publicKeyPath);
        log.info("已从 PEM 文件加载 JWT RSA 密钥对（kid={}）", keyId);
    }

    public RSAPublicKey publicKey() {
        return publicKey;
    }

    public RSAPrivateKey privateKey() {
        return privateKey;
    }

    public String keyId() {
        return keyId;
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(KEY_SIZE);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("无法生成 JWT RSA 密钥对", ex);
        }
    }

    private static RSAPrivateKey readPrivateKey(String path) {
        byte[] der = decodePem(readPem(path));
        try {
            return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (GeneralSecurityException | ClassCastException ex) {
            throw new IllegalStateException("JWT 私钥不是合法的 PKCS#8 RSA 私钥：" + path, ex);
        }
    }

    private static RSAPublicKey readPublicKey(String path) {
        byte[] der = decodePem(readPem(path));
        try {
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (GeneralSecurityException | ClassCastException ex) {
            throw new IllegalStateException("JWT 公钥不是合法的 X.509 RSA 公钥：" + path, ex);
        }
    }

    private static String readPem(String path) {
        try {
            return Files.readString(Path.of(path), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("读取 JWT 密钥文件失败：" + path, ex);
        }
    }

    private static byte[] decodePem(String pem) {
        StringBuilder base64 = new StringBuilder();
        pem.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("-----"))
                .forEach(base64::append);
        try {
            return Base64.getDecoder().decode(base64.toString());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("JWT 密钥不是合法的 PEM 内容", ex);
        }
    }

    private static boolean isProductionProfile(Environment environment) {
        return Arrays.stream(environment.getActiveProfiles()).anyMatch(PROD_PROFILE::equalsIgnoreCase);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
