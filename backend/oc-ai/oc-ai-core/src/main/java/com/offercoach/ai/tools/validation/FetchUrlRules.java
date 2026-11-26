package com.offercoach.ai.tools.validation;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 抓取 URL 的合法性校验（纵深防御）。
 *
 * <p>成员与 oc-app 的两道关卡共用：内部抓取服务执行前、编排层调用前。
 * 只允许 http/https、必须含 host、不得内嵌用户名密码、不得指向本机/私网/云元数据地址。</p>
 *
 * <p>这里只做基于字面量的判定，不做 DNS 解析：解析在抓取方（工具服务）侧进行，
 * 本侧解析既不能代表抓取方的网络视图，又会把用户可控的主机名变成请求线程上的阻塞调用。
 * 因此对「主机名解析到私网」这一类的最终拦截由抓取侧负责，本侧负责把字面量地址全部挡掉。</p>
 */
public final class FetchUrlRules {

    public static final int MAX_URL_LENGTH = 8192;

    private static final Pattern IPV4_LITERAL = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");

    /** 云元数据/内部服务常用主机名；字面量之外的第二道拦截。 */
    private static final String[] BLOCKED_HOST_SUFFIXES = {"localhost", "local", "internal", "metadata.google.internal"
    };

    private FetchUrlRules() {}

    /**
     * 校验并返回去除首尾空白后的 URL；不合法时抛 {@link IllegalArgumentException}。
     */
    public static String requireValid(String raw) {
        String value = raw == null ? "" : raw.strip();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("URL 不能为空");
        }
        if (value.length() > MAX_URL_LENGTH) {
            throw new IllegalArgumentException("URL 过长");
        }
        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("URL 不是合法的地址", exception);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new IllegalArgumentException("仅支持 http/https 链接");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("URL 缺少主机名");
        }
        if (uri.getUserInfo() != null) {
            throw new IllegalArgumentException("URL 不允许携带用户名或密码");
        }
        String lowerHost = stripBrackets(host.toLowerCase(Locale.ROOT));
        if (isBlockedHostName(lowerHost) || isNonPublicLiteral(lowerHost)) {
            throw new IllegalArgumentException("不允许抓取本机或元数据地址");
        }
        return value;
    }

    private static String stripBrackets(String host) {
        if (host.startsWith("[") && host.endsWith("]")) {
            return host.substring(1, host.length() - 1);
        }
        return host;
    }

    private static boolean isBlockedHostName(String host) {
        for (String suffix : BLOCKED_HOST_SUFFIXES) {
            if (host.equals(suffix) || host.endsWith("." + suffix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 字面量地址判定：IPv4 私网/保留段、IPv6 环回/链路本地/唯一本地地址。
     */
    static boolean isNonPublicLiteral(String host) {
        if (host.indexOf(':') >= 0) {
            return isNonPublicIpv6(host);
        }
        if (!IPV4_LITERAL.matcher(host).matches()) {
            return false;
        }
        String[] parts = host.split("\\.");
        int first;
        int second;
        try {
            first = Integer.parseInt(parts[0]);
            second = Integer.parseInt(parts[1]);
        } catch (NumberFormatException exception) {
            return false;
        }
        if (first == 0 || first == 10 || first == 127 || first >= 224) {
            // 0/8 本网络、10/8 私网、127/8 环回、224/4 组播、240/4 保留与广播
            return true;
        }
        if (first == 169 && second == 254) {
            // 169.254.0.0/16 链路本地，含 169.254.169.254 云元数据
            return true;
        }
        if (first == 172 && second >= 16 && second <= 31) {
            return true;
        }
        if (first == 192 && second == 168) {
            return true;
        }
        if (first == 100 && second >= 64 && second <= 127) {
            // 100.64.0.0/10 CGNAT
            return true;
        }
        if (first == 192 && second == 0) {
            // 192.0.0.0/24 IETF 协议分配、192.0.2.0/24 文档段
            return true;
        }
        return first == 198 && (second == 18 || second == 19 || second == 51);
    }

    private static boolean isNonPublicIpv6(String host) {
        String value = host;
        int scope = value.indexOf('%');
        if (scope >= 0) {
            value = value.substring(0, scope);
        }
        // IPv4 映射地址按内嵌 IPv4 判定，覆盖 ::ffff:127.0.0.1 这类绕过写法
        String mapped = mappedIpv4(value);
        if (mapped != null) {
            return isNonPublicLiteral(mapped);
        }
        String compact = value.replace(":", "");
        if (compact.isEmpty()) {
            // :: 未指定地址
            return true;
        }
        if (isIpv6Loopback(value)) {
            return true;
        }
        if (value.startsWith("fe8") || value.startsWith("fe9") || value.startsWith("fea") || value.startsWith("feb")) {
            // fe80::/10 链路本地
            return true;
        }
        if (value.startsWith("fc") || value.startsWith("fd")) {
            // fc00::/7 唯一本地地址
            return true;
        }
        return value.startsWith("ff");
    }

    /** 环回地址：::1 与完整写法 0:0:0:0:0:0:0:1。 */
    private static boolean isIpv6Loopback(String value) {
        return "::1".equals(value) || "0:0:0:0:0:0:0:1".equals(value);
    }

    /**
     * 提取 {@code ::ffff:a.b.c.d} 或 {@code ::ffff:7f00:1} 形式的内嵌 IPv4；非映射地址返回 null。
     */
    private static String mappedIpv4(String value) {
        if (!value.startsWith("::ffff:")) {
            return null;
        }
        String tail = value.substring("::ffff:".length());
        if (IPV4_LITERAL.matcher(tail).matches()) {
            return tail;
        }
        String[] groups = tail.split(":");
        if (groups.length != 2) {
            return null;
        }
        try {
            int high = Integer.parseInt(groups[0], 16);
            int low = Integer.parseInt(groups[1], 16);
            return (high >> 8) + "." + (high & 0xFF) + "." + (low >> 8) + "." + (low & 0xFF);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
