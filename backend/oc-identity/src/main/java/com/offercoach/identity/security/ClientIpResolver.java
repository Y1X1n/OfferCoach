package com.offercoach.identity.security;

import com.offercoach.identity.config.AuthProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Component;

/**
 * 客户端 IP 解析：仅当直连来源是回环/私网时才考虑代理头；配置代理密钥时要求密钥匹配。
 */
@Component
public class ClientIpResolver {

    public static final String PROXY_SECRET_HEADER = "X-Proxy-Secret";
    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String X_REAL_IP = "X-Real-IP";
    private static final String UNKNOWN_IP = "unknown";

    private final AuthProperties properties;

    public ClientIpResolver(AuthProperties properties) {
        this.properties = properties;
    }

    public String resolve(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        if (remoteAddress == null || remoteAddress.isBlank()) {
            return UNKNOWN_IP;
        }
        if (!isPrivateAddress(remoteAddress)) {
            return remoteAddress;
        }
        String proxySecret = properties.getProxySecret();
        if (proxySecret != null && !proxySecret.isBlank()) {
            String provided = request.getHeader(PROXY_SECRET_HEADER);
            if (!secretMatches(proxySecret, provided)) {
                return remoteAddress;
            }
            String realIp = request.getHeader(X_REAL_IP);
            return isIpLiteral(realIp == null ? "" : realIp.trim()) ? realIp.trim() : remoteAddress;
        }
        String realIp = request.getHeader(X_REAL_IP);
        if (realIp != null && isIpLiteral(realIp.trim())) {
            return realIp.trim();
        }
        String forwardedFor = request.getHeader(X_FORWARDED_FOR);
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return remoteAddress;
        }
        String first = forwardedFor.split(",")[0].trim();
        return isIpLiteral(first) ? first : remoteAddress;
    }

    private static boolean secretMatches(String expected, String provided) {
        if (provided == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), provided.trim().getBytes(StandardCharsets.UTF_8));
    }

    private static boolean isPrivateAddress(String address) {
        if (address.startsWith("::ffff:")) {
            return isPrivateAddress(address.substring("::ffff:".length()));
        }
        if ("::1".equals(address) || "0:0:0:0:0:0:0:1".equals(address)) {
            return true;
        }
        if (address.startsWith("127.")
                || address.startsWith("10.")
                || address.startsWith("192.168.")
                || address.startsWith("169.254.")
                || address.startsWith("fe80:")
                || address.startsWith("fc")
                || address.startsWith("fd")) {
            return true;
        }
        if (address.startsWith("172.")) {
            String[] parts = address.split("\\.");
            if (parts.length < 2) {
                return false;
            }
            try {
                int second = Integer.parseInt(parts[1]);
                return second >= 16 && second <= 31;
            } catch (NumberFormatException ex) {
                return false;
            }
        }
        return false;
    }

    private static boolean isIpLiteral(String value) {
        if (value.isEmpty() || value.length() > 45) {
            return false;
        }
        if (value.indexOf('.') < 0 && value.indexOf(':') < 0) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            boolean allowed = (ch >= '0' && ch <= '9')
                    || (ch >= 'a' && ch <= 'f')
                    || (ch >= 'A' && ch <= 'F')
                    || ch == ':'
                    || ch == '.';
            if (!allowed) {
                return false;
            }
        }
        return true;
    }
}
