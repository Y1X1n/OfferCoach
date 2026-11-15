package com.offercoach.ai.tools.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.offercoach.ai.AiRuntimeConfigService;
import com.offercoach.ai.AiRuntimeConfigService.AiRuntimeSettings;
import com.offercoach.ai.tools.config.ToolsProperties;
import com.offercoach.ai.tools.error.ToolErrorKind;
import com.offercoach.ai.tools.error.ToolException;
import com.offercoach.ai.tools.http.MultipartBody;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * DeepSeek 工具接口客户端：{@code /v1/tools/{asr,tts,ocr,search,fetch}}。
 *
 * <p>与对话接口同源同 Key，Base URL 与密钥统一来自 {@code ai_runtime_config}；
 * 本类只负责 HTTP 细节与错误映射，不做业务判断。密钥不写日志。</p>
 */
@Component
public class DeepSeekToolsClient {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekToolsClient.class);
    private static final int MAX_ERROR_BODY = 512;

    private final AiRuntimeConfigService runtimeConfig;
    private final ToolsProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public DeepSeekToolsClient(
            AiRuntimeConfigService runtimeConfig, ToolsProperties properties, ObjectMapper objectMapper) {
        this.runtimeConfig = runtimeConfig;
        this.properties = properties;
        this.objectMapper = objectMapper;
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
        MultipartBody.Body body =
                MultipartBody.of(List.of(new MultipartBody.Part("file", fileName, contentType, audio)));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uriOf(settings, "/tools/asr"))
                .timeout(properties.getAsrTimeout())
                .header("Authorization", "Bearer " + settings.apiKey())
                .header("Content-Type", body.contentType())
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.bytes()))
                .build();
        return sendForText(request);
    }

    /**
     * OCR：multipart 上传文件与 purpose，返回 JSON 结果原文。
     */
    public String ocr(byte[] file, String fileName, String contentType, String purpose) {
        AiRuntimeSettings settings = requireSettings();
        MultipartBody.Body body = MultipartBody.of(List.of(
                new MultipartBody.Part("purpose", null, null, purpose.getBytes(StandardCharsets.UTF_8)),
                new MultipartBody.Part("file", fileName, contentType, file)));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uriOf(settings, "/tools/ocr"))
                .timeout(properties.getOcrTimeout())
                .header("Authorization", "Bearer " + settings.apiKey())
                .header("Content-Type", body.contentType())
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.bytes()))
                .build();
        return sendForText(request);
    }

    /**
     * Search：JSON 请求，返回结构化检索结果原文。
     */
    public String search(String query, Integer count, String oriQuestion, Boolean needContent, String localization) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("query", query);
        if (count != null) {
            payload.put("count", count);
        }
        if (oriQuestion != null && !oriQuestion.isBlank()) {
            payload.put("ori_question", oriQuestion);
        }
        if (needContent != null) {
            payload.put("need_content", needContent);
        }
        if (localization != null && !localization.isBlank()) {
            payload.put("localization", localization);
        }
        return postJson(requireSettings(), "/tools/search", payload, properties.getSearchTimeout());
    }

    /**
     * Fetch：JSON 请求，返回页面正文（Markdown）原文。
     */
    public String fetch(String url) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("url", url);
        return postJson(requireSettings(), "/tools/fetch", payload, properties.getFetchTimeout());
    }

    /**
     * TTS：JSON 请求，流式返回音频字节；调用方负责透传或缓存。
     */
    public AudioStream tts(String text, String voiceId, String format) {
        AiRuntimeSettings settings = requireSettings();
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("text", text);
        payload.put("voice_id", voiceId);
        payload.put("format", format);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uriOf(settings, "/tools/tts"))
                .timeout(properties.getTtsTimeout())
                .header("Authorization", "Bearer " + settings.apiKey())
                .header("Content-Type", "application/json")
                .header("Accept", "audio/wav, audio/pcm, application/octet-stream")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
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

    private String postJson(AiRuntimeSettings settings, String path, ObjectNode payload, Duration timeout) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uriOf(settings, path))
                .timeout(timeout)
                .header("Authorization", "Bearer " + settings.apiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
                .build();
        return sendForText(request);
    }

    private String sendForText(HttpRequest request) {
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
        return uriOf(settings.baseUrl(), path);
    }

    /**
     * 拼接工具接口地址；baseUrl 已以 {@code /v1} 结尾时不再重复拼接 {@code /v1}。
     */
    static URI uriOf(String baseUrl, String path) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String prefix = base.endsWith("/v1") ? "" : "/v1";
        try {
            return URI.create(base + prefix + path);
        } catch (IllegalArgumentException exception) {
            throw new ToolException(ToolErrorKind.NOT_CONFIGURED, "AI baseUrl 不合法", exception);
        }
    }

    private ToolException mapUpstream(int status, String body) {
        log.warn("工具上游返回非 2xx：status={} body={}", status, truncate(body));
        return new ToolException(classifyUpstream(status, body), "上游返回 " + status, null, status);
    }

    /**
     * 上游状态码 → 内部错误分类；400 且内容审核拦截时归为内容被拒。
     */
    static ToolErrorKind classifyUpstream(int status, String body) {
        return switch (status) {
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
}
