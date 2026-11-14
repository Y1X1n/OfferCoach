package com.offercoach.ai.tools.validation;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * 抓取 URL 的合法性校验（纵深防御）。
 *
 * <p>成员与 oc-app 的两道关卡共用：内部抓取服务执行前、编排层调用前。
 * 只允许 http/https、必须含 host、不得内嵌用户名密码、不得指向本机或元数据地址。</p>
 */
public final class FetchUrlRules {

    public static final int MAX_URL_LENGTH = 8192;

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
        String lowerHost = host.toLowerCase(Locale.ROOT);
        if ("localhost".equals(lowerHost)
                || lowerHost.endsWith(".localhost")
                || "127.0.0.1".equals(lowerHost)
                || "0.0.0.0".equals(lowerHost)
                || "::1".equals(lowerHost)
                || "[::1]".equals(lowerHost)
                || "metadata.google.internal".equals(lowerHost)) {
            throw new IllegalArgumentException("不允许抓取本机或元数据地址");
        }
        return value;
    }
}
