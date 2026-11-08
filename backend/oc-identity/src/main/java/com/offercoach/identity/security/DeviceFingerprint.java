package com.offercoach.identity.security;

import com.offercoach.identity.config.AuthProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * 注册设备指纹：优先客户端持久化 UUID，缺失时回退 UA + 来源地址；
 * 结果只存 SHA-256，用于同设备注册风控与自推荐识别。
 */
@Component
public class DeviceFingerprint {

    private static final String DEVICE_HEADER = "X-Device-Id";
    private static final int MAX_DEVICE_ID = 128;

    private final String salt;

    public DeviceFingerprint(AuthProperties properties) {
        this.salt = properties.getDeviceSalt() == null ? "" : properties.getDeviceSalt();
    }

    public String hash(HttpServletRequest request) {
        String deviceId = request.getHeader(DEVICE_HEADER);
        String userAgent = request.getHeader("User-Agent");
        String basis;
        if (deviceId != null && !deviceId.isBlank()) {
            String trimmed = deviceId.trim();
            basis = "id:" + (trimmed.length() <= MAX_DEVICE_ID ? trimmed : trimmed.substring(0, MAX_DEVICE_ID)) + "|ua:"
                    + safe(userAgent);
        } else {
            basis = "ua:" + safe(userAgent) + "|ip:" + safe(request.getRemoteAddr());
        }
        return sha256(salt + "|" + basis);
    }

    private static String safe(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= 256 ? value : value.substring(0, 256);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }
}
