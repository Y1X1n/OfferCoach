package com.offercoach.ai.tools.client;

import com.offercoach.ai.AiRuntimeConfigService;
import com.offercoach.ai.AiRuntimeConfigService.AiRuntimeSettings;
import com.offercoach.ai.tools.config.ToolsProperties;
import com.offercoach.ai.tools.error.ToolErrorKind;
import com.offercoach.ai.tools.error.ToolException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * DeepSeek 工具接口客户端：{@code /v1/tools/{asr,tts,ocr}}。
 *
 * <p>与对话接口同源同 Key，Base URL 与密钥统一来自 {@code ai_runtime_config}；
 * 本类只负责 HTTP 细节与错误映射，不做业务判断。密钥不写日志。</p>
 */
@Component
public class DeepSeekToolsClient {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekToolsClient.class);
    private static final String PROVIDER = "deepseek-tools";
    private static final int MAX_ERROR_BODY = 512;
    private static final String BOUNDARY_PREFIX = "----ocAiTools";

    private final AiRuntimeConfigService runtimeConfig;
    private final ToolsProperties properties;
    private final HttpClient httpClient;

    public DeepSeekToolsClient(AiRuntimeConfigService runtimeConfig, ToolsProperties properties) {
        this.runtimeConfig = runtimeConfig;
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /**
     * 当前配置是否可用于工具调用。
     */
    public boolean available() {
        return usable(runtimeConfig.current());
    }

    /**
     * ASR：multipart 上传音频，返回 JSON 转写结果原文。
     */
    public String asr(byte[] audio, String fileName, String contentType) {
        AiRuntimeSettings settings = requireSettings();
        Multipart body = multipart(List.of(new Part("file", fileName, contentType, audio)));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uriOf(settings, "/tools/asr"))
                .timeout(properties.getAsrTimeout())
                .header("Authorization", "Bearer " + settings.apiKey())
                .header("Content-Type", body.contentType())
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.bytes()))
                .build();
        return sendForText(request, properties.getAsrTimeout());
    }

    /**
     * OCR：multipart 上传文件与 purpose，返回 JSON 结果原文。
     */
    public String ocr(byte[] file, String fileName, String contentType, String purpose) {
        AiRuntimeSettings settings = requireSettings();
        Multipart body = multipart(List.of(
                new Part("purpose", null, null, purpose.getBytes(StandardCharsets.UTF_8)),
                new Part("file", fileName, contentType, file)));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uriOf(settings, "/tools/ocr"))
                .timeout(properties.getOcrTimeout())
                .header("Authorization", "Bearer " + settings.apiKey())
                .header("Content-Type", body.contentType())
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.bytes()))
                .build();
        return sendForText(request, properties.getOcrTimeout());
    }

    /**
     * Search：JSON 请求，返回结构化检索结果原文。
     */
    public String search(String query, Integer count, String oriQuestion, Boolean needContent, String localization) {
        AiRuntimeSettings settings = requireSettings();
        StringBuilder json = new StringBuilder("{\"query\":").append(quote(query));
        if (count != null) {
            json.append(",\"count\":").append(count);
        }
        if (oriQuestion != null && !oriQuestion.isBlank()) {
            json.append(",\"ori_question\":").append(quote(oriQuestion));
        }
        if (needContent != null) {
            json.append(",\"need_content\":").append(needContent);
        }
        if (localization != null && !localization.isBlank()) {
            json.append(",\"localization\":").append(quote(localization));
        }
        json.append('}');
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uriOf(settings, "/tools/search"))
                .timeout(properties.getSearchTimeout())
                .header("Authorization", "Bearer " + settings.apiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.toString(), StandardCharsets.UTF_8))
                .build();
        return sendForText(request, properties.getSearchTimeout());
    }

    /**
     * Fetch：JSON 请求，返回页面正文（Markdown）原文。
     */
    public String fetch(String url) {
        AiRuntimeSettings settings = requireSettings();
        String json = "{\"url\":" + quote(url) + "}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uriOf(settings, "/tools/fetch"))
                .timeout(properties.getFetchTimeout())
                .header("Authorization", "Bearer " + settings.apiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        return sendForText(request, properties.getFetchTimeout());
    }

    /**
     * TTS：JSON 请求，流式返回音频字节；调用方负责透传或缓存。
     */
    public AudioStream tts(String text, String voiceId, String format) {
        AiRuntimeSettings settings = requireSettings();
        String json =
                "{\"text\":" + quote(text) + ",\"voice_id\":" + quote(voiceId) + ",\"format\":" + quote(format) + "}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uriOf(settings, "/tools/tts"))
                .timeout(properties.getTtsTimeout())
                .header("Authorization", "Bearer " + settings.apiKey())
                .header("Content-Type", "application/json")
                .header("Accept", "audio/wav, audio/pcm, application/octet-stream")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String errorBody = readQuietly(response.body());
                throw mapUpstream(response.statusCode(), errorBody);
            }
            String contentType = response.headers().firstValue("Content-Type").orElse("audio/wav");
            return new AudioStream(response.body(), contentType);
        } catch (IOException exception) {
            throw new ToolException(ToolErrorKind.UPSTREAM_FAILED, "TTS 上游请求失败", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ToolException(ToolErrorKind.UPSTREAM_FAILED, "TTS 上游请求被中断", exception);
        }
    }

    /**
     * 供日志记录使用的模型标识。
     */
    public String providerName() {
        return PROVIDER;
    }

    private String sendForText(HttpRequest request, Duration timeout) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw mapUpstream(response.statusCode(), response.body());
            }
            return response.body();
        } catch (IOException exception) {
            throw new ToolException(ToolErrorKind.UPSTREAM_FAILED, "工具上游请求失败", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ToolException(ToolErrorKind.UPSTREAM_FAILED, "工具上游请求被中断", exception);
        }
    }

    private AiRuntimeSettings requireSettings() {
        AiRuntimeSettings settings = runtimeConfig.current();
        if (!usable(settings)) {
            throw new ToolException(ToolErrorKind.NOT_CONFIGURED);
        }
        if (!runtimeConfig.isSafeRuntimeUrl(settings.baseUrl())) {
            throw new ToolException(ToolErrorKind.NOT_CONFIGURED, "AI 网关地址不安全");
        }
        return settings;
    }

    private static boolean usable(AiRuntimeSettings settings) {
        return settings != null
                && settings.enabled()
                && settings.apiKey() != null
                && !settings.apiKey().isBlank()
                && settings.baseUrl() != null
                && !settings.baseUrl().isBlank();
    }

    private URI uriOf(AiRuntimeSettings settings, String path) {
        String base = settings.baseUrl().endsWith("/")
                ? settings.baseUrl().substring(0, settings.baseUrl().length() - 1)
                : settings.baseUrl();
        // 运行时配置默认已带 /v1（https://api.deepseek.com/v1），避免重复拼接成 /v1/v1/tools。
        String prefix = base.endsWith("/v1") ? "" : "/v1";
        try {
            return URI.create(base + prefix + path);
        } catch (IllegalArgumentException exception) {
            throw new ToolException(ToolErrorKind.NOT_CONFIGURED, "AI baseUrl 不合法", exception);
        }
    }

    private ToolException mapUpstream(int status, String body) {
        log.warn("工具上游返回非 2xx：status={} body={}", status, truncate(body));
        ToolErrorKind kind =
                switch (status) {
                    case 400 -> body != null && body.contains("Content Exists Risk")
                            ? ToolErrorKind.CONTENT_REJECTED
                            : ToolErrorKind.INVALID_INPUT;
                    case 402 -> ToolErrorKind.INSUFFICIENT_BALANCE;
                    case 403 -> ToolErrorKind.BLOCKED;
                    case 404 -> ToolErrorKind.NO_CONTENT;
                    case 408 -> ToolErrorKind.UPSTREAM_TIMEOUT;
                    case 413 -> ToolErrorKind.TOO_LARGE;
                    case 429 -> ToolErrorKind.UPSTREAM_BUSY;
                    case 503 -> ToolErrorKind.UPSTREAM_BUSY;
                    case 504 -> ToolErrorKind.UPSTREAM_TIMEOUT;
                    default -> ToolErrorKind.UPSTREAM_FAILED;
                };
        return new ToolException(kind, "上游返回 " + status, null, status);
    }

    private static String readQuietly(InputStream input) {
        try (InputStream stream = input) {
            return new String(stream.readNBytes(MAX_ERROR_BODY), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            return "";
        }
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= MAX_ERROR_BODY ? value : value.substring(0, MAX_ERROR_BODY);
    }

    private static String quote(String value) {
        StringBuilder builder = new StringBuilder("\"");
        for (char ch : (value == null ? "" : value).toCharArray()) {
            switch (ch) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        builder.append(String.format("\\u%04x", (int) ch));
                    } else {
                        builder.append(ch);
                    }
                }
            }
        }
        return builder.append('"').toString();
    }

    private static Multipart multipart(List<Part> parts) {
        String boundary = BOUNDARY_PREFIX + UUID.randomUUID().toString().replace("-", "");
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (Part part : parts) {
            StringBuilder header = new StringBuilder();
            header.append("--").append(boundary).append("\r\n");
            header.append("Content-Disposition: form-data; name=\"")
                    .append(part.name())
                    .append('"');
            if (part.fileName() != null) {
                header.append("; filename=\"").append(part.fileName()).append('"');
            }
            header.append("\r\n");
            if (part.contentType() != null) {
                header.append("Content-Type: ").append(part.contentType()).append("\r\n");
            }
            header.append("\r\n");
            out.writeBytes(header.toString().getBytes(StandardCharsets.UTF_8));
            out.writeBytes(part.content());
            out.writeBytes("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        out.writeBytes(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return new Multipart(out.toByteArray(), "multipart/form-data; boundary=" + boundary);
    }

    /**
     * TTS 音频流；调用方必须在读取完毕后关闭。
     */
    public record AudioStream(InputStream body, String contentType) implements AutoCloseable {

        @Override
        public void close() {
            try {
                body.close();
            } catch (IOException ignored) {
                // 关闭失败无需上抛。
            }
        }
    }

    private record Part(String name, String fileName, String contentType, byte[] content) {}

    private record Multipart(byte[] bytes, String contentType) {}
}
