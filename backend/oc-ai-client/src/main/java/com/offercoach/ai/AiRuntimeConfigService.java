package com.offercoach.ai;

import com.offercoach.common.error.BizException;
import com.offercoach.common.error.ErrorCode;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 读取管理端在线配置的 AI 运行时参数；单行配置空字段时回退 oc.ai.* 环境默认值，缓存 30 秒。
 */
@Component
public class AiRuntimeConfigService {

    private static final Logger log = LoggerFactory.getLogger(AiRuntimeConfigService.class);
    private static final long CACHE_TTL_MILLIS = 30_000L;
    private static final int MAX_URL_LENGTH = 512;
    private static final int MAX_MODEL_LENGTH = 128;
    private static final String SELECT_SQL =
            """
            SELECT enabled, base_url, api_key_cipher, model, small_model, updated_by, updated_at
            FROM ai_runtime_config
            WHERE id = 1
            """;
    private static final String UPDATE_WITH_KEY_SQL =
            """
            UPDATE ai_runtime_config
            SET enabled = ?, base_url = ?, api_key_cipher = ?, model = ?, small_model = ?,
                updated_by = ?, updated_at = now()
            WHERE id = 1
            """;
    private static final String UPDATE_KEEP_KEY_SQL =
            """
            UPDATE ai_runtime_config
            SET enabled = ?, base_url = ?, model = ?, small_model = ?, updated_by = ?, updated_at = now()
            WHERE id = 1
            """;
    private static final String INSERT_SQL =
            """
            INSERT INTO ai_runtime_config
                (id, enabled, base_url, api_key_cipher, model, small_model, updated_by, updated_at)
            VALUES (1, ?, ?, ?, ?, ?, ?, now())
            """;

    private final JdbcTemplate jdbcTemplate;
    private final AiProperties properties;
    private final SecretCipher secretCipher;
    private volatile CacheEntry cache;

    public AiRuntimeConfigService(JdbcTemplate jdbcTemplate, AiProperties properties, SecretCipher secretCipher) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.secretCipher = secretCipher;
    }

    /**
     * 当前生效配置；数据库不可用时回退环境默认值，保证业务调用不因配置表异常中断。
     */
    public AiRuntimeSettings current() {
        CacheEntry cached = cache;
        long now = System.currentTimeMillis();
        if (cached != null && cached.expiresAtMillis() > now) {
            return cached.settings();
        }
        AiRuntimeSettings settings = settingsOf(readRow());
        cache = new CacheEntry(settings, now + CACHE_TTL_MILLIS);
        return settings;
    }

    /**
     * 管理端展示快照；只暴露是否配置与末 4 位掩码，绝不返回明文 Key。
     */
    public AiRuntimeSnapshot snapshot() {
        RuntimeRow row = readRow();
        AiRuntimeSettings settings = settingsOf(row);
        boolean hasApiKey = settings.apiKey() != null && !settings.apiKey().isBlank();
        return new AiRuntimeSnapshot(
                settings.enabled(),
                settings.baseUrl(),
                settings.model(),
                settings.smallModel(),
                hasApiKey,
                hasApiKey ? SecretCipher.mask(settings.apiKey()) : null,
                row == null ? null : row.updatedAt(),
                row == null ? null : row.updatedBy());
    }

    /**
     * 更新运行时配置并立即失效缓存；apiKey 为空保留旧值，clearApiKey=true 写空串表示显式清除。
     */
    public AiRuntimeSnapshot update(
            Long actorId,
            boolean enabled,
            String baseUrl,
            String model,
            String smallModel,
            String apiKey,
            boolean clearApiKey) {
        String normalizedBaseUrl = requireBaseUrl(baseUrl);
        String normalizedModel = requireModel(model);
        String normalizedSmallModel = optionalModel(smallModel);
        if (clearApiKey && apiKey != null && !apiKey.isBlank()) {
            throw fieldError("apiKey", "与 clearApiKey 不能同时提交");
        }
        String cipher = null;
        if (clearApiKey) {
            cipher = "";
        } else if (apiKey != null && !apiKey.isBlank()) {
            cipher = secretCipher.encrypt(apiKey.strip());
        }
        int updated = cipher == null
                ? jdbcTemplate.update(
                        UPDATE_KEEP_KEY_SQL, enabled, normalizedBaseUrl, normalizedModel, normalizedSmallModel, actorId)
                : jdbcTemplate.update(
                        UPDATE_WITH_KEY_SQL,
                        enabled,
                        normalizedBaseUrl,
                        cipher,
                        normalizedModel,
                        normalizedSmallModel,
                        actorId);
        if (updated == 0) {
            jdbcTemplate.update(
                    INSERT_SQL, enabled, normalizedBaseUrl, cipher, normalizedModel, normalizedSmallModel, actorId);
        }
        invalidate();
        return snapshot();
    }

    /**
     * 主动失效缓存，供管理端保存后立即生效。
     */
    public void invalidate() {
        cache = null;
    }

    private AiRuntimeSettings settingsOf(RuntimeRow row) {
        RuntimeRow effective = row == null ? RuntimeRow.EMPTY : row;
        boolean enabled = effective.enabled() == null ? properties.isEnabled() : effective.enabled();
        String baseUrl = firstNonBlank(effective.baseUrl(), properties.getBaseUrl());
        String model = firstNonBlank(effective.model(), properties.getModel());
        String smallModel = firstNonBlank(effective.smallModel(), properties.getSmallModel());
        String apiKey = effective.apiKeyCipher() == null
                ? nullToEmpty(properties.getApiKey())
                : secretCipher.decrypt(effective.apiKeyCipher());
        return new AiRuntimeSettings(
                enabled,
                baseUrl,
                apiKey,
                model,
                smallModel,
                properties.getTimeout(),
                properties.getMaxTokens(),
                properties.getTemperature());
    }

    private RuntimeRow readRow() {
        try {
            List<RuntimeRow> rows = jdbcTemplate.query(
                    SELECT_SQL,
                    (resultSet, index) -> new RuntimeRow(
                            resultSet.getObject("enabled", Boolean.class),
                            resultSet.getString("base_url"),
                            resultSet.getString("api_key_cipher"),
                            resultSet.getString("model"),
                            resultSet.getString("small_model"),
                            resultSet.getObject("updated_by", Long.class),
                            resultSet.getObject("updated_at", OffsetDateTime.class)));
            return rows.isEmpty() ? null : rows.getFirst();
        } catch (RuntimeException exception) {
            log.warn("AI 运行时配置读取失败，回退环境默认值：reason={}", exception.getClass().getSimpleName());
            return null;
        }
    }

    String requireBaseUrl(String raw) {
        String normalized = validateBaseUrl(raw, properties.isAllowPrivateHosts());
        if (!properties.isAllowPrivateHosts()) {
            requirePublicResolution(hostOf(normalized));
        }
        return normalized;
    }

    /**
     * 请求前复查：防止存量配置或 DNS 重绑定在请求阶段指向内网。
     */
    public boolean isSafeRuntimeUrl(String raw) {
        try {
            String normalized = validateBaseUrl(raw, properties.isAllowPrivateHosts());
            if (!properties.isAllowPrivateHosts()) {
                requirePublicResolution(hostOf(normalized));
            }
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static String hostOf(String url) {
        try {
            String host = new URI(url).getHost();
            if (host == null || host.isBlank()) {
                throw fieldError("baseUrl", "不是合法的 URL");
            }
            return host;
        } catch (URISyntaxException exception) {
            throw fieldError("baseUrl", "不是合法的 URL");
        }
    }

    static String validateBaseUrl(String raw, boolean allowPrivateHosts) {
        String value = optionalText(raw);
        if (value == null) {
            throw fieldError("baseUrl", "不能为空");
        }
        if (value.length() > MAX_URL_LENGTH) {
            throw fieldError("baseUrl", "长度不能超过 " + MAX_URL_LENGTH);
        }
        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException exception) {
            throw fieldError("baseUrl", "不是合法的 URL");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost();
        if (uri.getUserInfo() != null) {
            throw fieldError("baseUrl", "不允许在 URL 中包含用户信息");
        }
        if (!"https".equals(scheme) && !"http".equals(scheme)) {
            throw fieldError("baseUrl", "仅支持 http/https URL");
        }
        if (host == null || host.isBlank()) {
            throw fieldError("baseUrl", "不是合法的 URL");
        }
        if ("http".equals(scheme) && !isLocalHost(host)) {
            throw fieldError("baseUrl", "仅本机测试允许 http://localhost 或 http://127.0.0.1");
        }
        if (!allowPrivateHosts && isPrivateHost(host)) {
            throw fieldError("baseUrl", "AI 网关不允许指向内网或本机地址");
        }
        if (!allowPrivateHosts && isIpLiteral(host)) {
            try {
                if (isNonPublicAddress(InetAddress.getByName(host))) {
                    throw fieldError("baseUrl", "AI 网关不允许指向内网或本机地址");
                }
            } catch (UnknownHostException exception) {
                throw fieldError("baseUrl", "不是合法的 URL");
            }
        }
        return trimTrailingSlash(value);
    }

    private static boolean isIpLiteral(String host) {
        return host.indexOf(':') >= 0 || host.matches("\\d{1,3}(\\.\\d{1,3}){3}");
    }

    /**
     * 解析主机并拒绝任一非公网地址，拦截 localhost 别名、内网域名与 DNS 重绑定到私网的情况。
     */
    private static void requirePublicResolution(String host) {
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException exception) {
            throw fieldError("baseUrl", "无法解析主机名");
        }
        for (InetAddress address : addresses) {
            if (isNonPublicAddress(address)) {
                throw fieldError("baseUrl", "AI 网关不允许指向内网或本机地址");
            }
        }
    }

    static boolean isNonPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int first = bytes[0] & 0xFF;
            int second = bytes[1] & 0xFF;
            if (first == 0 || first == 127 || first >= 224) {
                return true;
            }
            if (first == 100 && second >= 64 && second <= 127) {
                return true;
            }
            if (first == 192 && second == 0) {
                return true;
            }
            if (first == 198 && (second == 18 || second == 19 || second == 51)) {
                return true;
            }
            return first == 203 && second == 0 && (bytes[2] & 0xFF) == 113;
        }
        if (bytes.length == 16) {
            int first = bytes[0] & 0xFF;
            return first == 0xFC || first == 0xFD;
        }
        return false;
    }

    private static boolean isLocalHost(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        return "localhost".equals(normalized) || "127.0.0.1".equals(normalized) || "::1".equals(normalized);
    }

    private static boolean isPrivateHost(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        if (isLocalHost(normalized)) {
            return true;
        }
        if (normalized.startsWith("10.")
                || normalized.startsWith("192.168.")
                || normalized.startsWith("169.254.")
                || normalized.startsWith("fe80:")
                || normalized.startsWith("fc")
                || normalized.startsWith("fd")) {
            return true;
        }
        if (normalized.startsWith("172.")) {
            String[] parts = normalized.split("\\.");
            if (parts.length < 2) {
                return false;
            }
            try {
                int second = Integer.parseInt(parts[1]);
                return second >= 16 && second <= 31;
            } catch (NumberFormatException exception) {
                return false;
            }
        }
        return false;
    }

    private static String requireModel(String value) {
        String normalized = optionalText(value);
        if (normalized == null) {
            throw fieldError("model", "不能为空");
        }
        if (normalized.length() > MAX_MODEL_LENGTH) {
            throw fieldError("model", "长度不能超过 " + MAX_MODEL_LENGTH);
        }
        return normalized;
    }

    private static String optionalModel(String value) {
        String normalized = optionalText(value);
        if (normalized != null && normalized.length() > MAX_MODEL_LENGTH) {
            throw fieldError("smallModel", "长度不能超过 " + MAX_MODEL_LENGTH);
        }
        return normalized;
    }

    private static String optionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }

    private static String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static BizException fieldError(String field, String message) {
        return new BizException(ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of(field, message)));
    }

    public record AiRuntimeSettings(
            boolean enabled,
            String baseUrl,
            String apiKey,
            String model,
            String smallModel,
            Duration timeout,
            int maxTokens,
            double temperature) {}

    public record AiRuntimeSnapshot(
            boolean enabled,
            String baseUrl,
            String model,
            String smallModel,
            boolean hasApiKey,
            String apiKeyMasked,
            OffsetDateTime updatedAt,
            Long updatedBy) {}

    private record RuntimeRow(
            Boolean enabled,
            String baseUrl,
            String apiKeyCipher,
            String model,
            String smallModel,
            Long updatedBy,
            OffsetDateTime updatedAt) {

        private static final RuntimeRow EMPTY = new RuntimeRow(null, null, null, null, null, null, null);
    }

    private record CacheEntry(AiRuntimeSettings settings, long expiresAtMillis) {}
}
