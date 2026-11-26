package com.offercoach.identity.security;

import com.offercoach.identity.config.AuthProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 客户端 IP 解析：仅当直连来源是回环/私网、且请求携带匹配的代理密钥时才采信代理头。
 *
 * <p>未配置 {@code oc.auth.proxy-secret} 时一律回退直连地址（fail-closed）：没有密钥就无法区分
 * 「Nginx 覆写的 X-Real-IP」和「客户端自带的 X-Real-IP/X-Forwarded-For」，采信任何一个都等于让
 * 调用方自选 IP，可绕过按 IP 的登录/注册限流并污染审计。生产 profile 由
 * {@code ProductionSafetyValidator} 强制要求该密钥。</p>
 */
@Component
public class ClientIpResolver {

    public static final String PROXY_SECRET_HEADER = "X-Proxy-Secret";
    private static final Logger log = LoggerFactory.getLogger(ClientIpResolver.class);
    private static final long WARN_INTERVAL_MILLIS = 60_000L;
    private static final String X_REAL_IP = "X-Real-IP";
    private static final String UNKNOWN_IP = "unknown";

    private final AuthProperties properties;
    private final AtomicLong lastSecretWarnAt = new AtomicLong(0L);
    private final AtomicLong lastMissingConfigWarnAt = new AtomicLong(0L);

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
        if (proxySecret == null || proxySecret.isBlank()) {
            warnMissingConfig();
            return remoteAddress;
        }
        String provided = request.getHeader(PROXY_SECRET_HEADER);
        if (!secretMatches(proxySecret, provided)) {
            warnMissingSecret();
            return remoteAddress;
        }
        String realIp = request.getHeader(X_REAL_IP);
        return isIpLiteral(realIp == null ? "" : realIp.trim()) ? realIp.trim() : remoteAddress;
    }

    /**
     * 未配置代理密钥时每分钟告警一次：本地开发可忽略，非本地环境属于漏配。
     */
    private void warnMissingConfig() {
        long now = System.currentTimeMillis();
        long previous = lastMissingConfigWarnAt.get();
        if (now - previous < WARN_INTERVAL_MILLIS || !lastMissingConfigWarnAt.compareAndSet(previous, now)) {
            return;
        }
        log.warn("未配置 oc.auth.proxy-secret，已忽略全部代理头并按直连地址识别客户端");
    }

    /**
     * 启动配置了代理密钥但请求未携带/不匹配时，每分钟告警一次，便于发现 Nginx 漏配。
     */
    private void warnMissingSecret() {
        long now = System.currentTimeMillis();
        long previous = lastSecretWarnAt.get();
        if (now - previous < WARN_INTERVAL_MILLIS || !lastSecretWarnAt.compareAndSet(previous, now)) {
            return;
        }
        log.warn("代理密钥缺失或不匹配，已回退为直连地址；请检查 Nginx X-Proxy-Secret 配置");
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
