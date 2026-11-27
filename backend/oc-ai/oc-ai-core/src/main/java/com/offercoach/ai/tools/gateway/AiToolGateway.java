package com.offercoach.ai.tools.gateway;

import com.offercoach.ai.tools.api.ToolDtos;
import com.offercoach.ai.tools.config.ToolsProperties;
import com.offercoach.ai.tools.error.ToolErrorKind;
import com.offercoach.ai.tools.error.ToolException;
import com.offercoach.ai.tools.http.MultipartBody;
import com.offercoach.common.trace.TraceContext;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 调用 AI 工具微服务的客户端；oc-app 是它们唯一的调用方。
 *
 * <p>工具服务只做「文件 ↔ 文本 / 音频」转换，鉴权、额度与降级决策留在本侧。
 * 所有调用透传当前请求的 {@code X-OC-Request-Id} 以串联跨服务 Trace；
 * 可重试错误（上游繁忙 / 超时 / 网络）退避重试一次。</p>
 */
@Component
public class AiToolGateway {

    private static final Logger log = LoggerFactory.getLogger(AiToolGateway.class);
    private static final int MAX_ERROR_BODY = 512;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final int MAX_ATTEMPTS = 2;
    private static final long RETRY_BACKOFF_MS = 300;

    private final ToolsProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public AiToolGateway(ToolsProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /**
     * 调用 ASR 服务；上游不可用时抛 {@link ToolException}，由调用方降级。
     */
    public ToolDtos.AsrResult transcribe(long userId, byte[] audio, String fileName, String contentType) {
        MultipartBody.Body body =
                MultipartBody.of(List.of(new MultipartBody.Part("file", fileName, contentType, audio)));
        HttpRequest request = baseRequest(properties.getAsrBaseUrl(), "/internal/v1/asr", userId)
                .timeout(properties.getAsrTimeout())
                .header("Content-Type", body.contentType())
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.bytes()))
                .build();
        String json = send(request);
        return parseAsr(objectMapper, json);
    }

    /**
     * 调用 TTS 服务，返回音频字节与内容类型。
     */
    public AudioResult speak(long userId, String text, String voiceId, String format) {
        String payload = objectMapper
                .createObjectNode()
                .put("text", text)
                .put("voiceId", voiceId)
                .put("format", format)
                .toString();
        HttpRequest request = baseRequest(properties.getTtsBaseUrl(), "/internal/v1/tts", userId)
                .timeout(properties.getTtsTimeout())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();
        ToolException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    String contentType =
                            response.headers().firstValue("Content-Type").orElse("audio/wav");
                    boolean cached = response.headers()
                            .firstValue("X-OC-Tts-Cache")
                            .map("HIT"::equalsIgnoreCase)
                            .orElse(false);
                    return new AudioResult(response.body(), contentType, cached);
                }
                last = mapUpstream(response.statusCode(), new String(response.body(), StandardCharsets.UTF_8));
            } catch (IOException exception) {
                last = new ToolException(ToolErrorKind.UPSTREAM_FAILED, "TTS 服务调用失败", exception);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new ToolException(ToolErrorKind.UPSTREAM_FAILED, "TTS 服务调用被中断", exception);
            }
            if (!last.kind().retryable() || attempt == MAX_ATTEMPTS) {
                throw last;
            }
            pauseBeforeRetry("TTS", last.kind(), attempt);
        }
        throw last;
    }

    /**
     * 调用 OCR 服务；用于扫描件简历降级识别。
     */
    public ToolDtos.OcrResult recognize(long userId, byte[] file, String fileName, String contentType, String purpose) {
        MultipartBody.Body body = MultipartBody.of(List.of(
                new MultipartBody.Part("purpose", null, null, purpose.getBytes(StandardCharsets.UTF_8)),
                new MultipartBody.Part("file", fileName, contentType, file)));
        HttpRequest request = baseRequest(properties.getOcrBaseUrl(), "/internal/v1/ocr", userId)
                .timeout(properties.getOcrTimeout())
                .header("Content-Type", body.contentType())
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.bytes()))
                .build();
        String json = send(request);
        return parseOcr(objectMapper, json);
    }

    /**
     * 调用 Search 服务；返回结构化检索结果。
     */
    public List<ToolDtos.SearchResult> search(
            long userId, String query, int count, String oriQuestion, boolean needContent) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("query", query);
        payload.put("count", count);
        payload.put("needContent", needContent);
        if (oriQuestion != null && !oriQuestion.isBlank()) {
            payload.put("oriQuestion", oriQuestion);
        }
        HttpRequest request = baseRequest(properties.getSearchBaseUrl(), "/internal/v1/search", userId)
                .timeout(properties.getSearchTimeout())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
                .build();
        String json = send(request);
        return parseSearch(objectMapper, json);
    }

    /**
     * 调用 Fetch 服务；返回页面标题与 Markdown 正文。
     */
    public ToolDtos.FetchResponse fetch(long userId, String url) {
        String payload = objectMapper.createObjectNode().put("url", url).toString();
        HttpRequest request = baseRequest(properties.getFetchBaseUrl(), "/internal/v1/fetch", userId)
                .timeout(properties.getFetchTimeout())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();
        String json = send(request);
        return parseFetch(objectMapper, json);
    }

    static ToolDtos.AsrResult parseAsr(ObjectMapper mapper, String json) {
        try {
            JsonNode root = mapper.readTree(json);
            return new ToolDtos.AsrResult(
                    root.path("requestId").asText(""),
                    root.path("text").asText(""),
                    root.path("durationMs").isNumber() ? root.path("durationMs").asInt() : null);
        } catch (JacksonException exception) {
            throw new ToolException(ToolErrorKind.UPSTREAM_FAILED, "ASR 结果解析失败", exception);
        }
    }

    static ToolDtos.OcrResult parseOcr(ObjectMapper mapper, String json) {
        try {
            JsonNode root = mapper.readTree(json);
            List<ToolDtos.OcrPage> pages = new ArrayList<>();
            for (JsonNode page : root.path("pages")) {
                pages.add(new ToolDtos.OcrPage(
                        page.path("index").asInt(pages.size()),
                        page.path("text").asText(""),
                        page.path("finishReason").asText("stop")));
            }
            return new ToolDtos.OcrResult(
                    root.path("id").asText(""), root.path("pageCount").asInt(pages.size()), pages);
        } catch (JacksonException exception) {
            throw new ToolException(ToolErrorKind.UPSTREAM_FAILED, "OCR 结果解析失败", exception);
        }
    }

    static List<ToolDtos.SearchResult> parseSearch(ObjectMapper mapper, String json) {
        try {
            JsonNode root = mapper.readTree(json);
            List<ToolDtos.SearchResult> results = new ArrayList<>();
            for (JsonNode item : root.path("results")) {
                results.add(new ToolDtos.SearchResult(
                        item.path("rerankScore").asDouble(0),
                        item.path("title").asText(""),
                        item.path("url").asText(""),
                        item.path("snippet").asText(""),
                        item.path("summary").asText(""),
                        item.path("content").asText(""),
                        textOrNull(item.path("siteName")),
                        textOrNull(item.path("siteIcon")),
                        textOrNull(item.path("date")),
                        item.path("authority").asInt(-1)));
            }
            return results;
        } catch (JacksonException exception) {
            throw new ToolException(ToolErrorKind.UPSTREAM_FAILED, "检索结果解析失败", exception);
        }
    }

    static ToolDtos.FetchResponse parseFetch(ObjectMapper mapper, String json) {
        try {
            JsonNode root = mapper.readTree(json);
            return new ToolDtos.FetchResponse(
                    root.path("title").asText(""), root.path("content").asText(""));
        } catch (JacksonException exception) {
            throw new ToolException(ToolErrorKind.UPSTREAM_FAILED, "抓取结果解析失败", exception);
        }
    }

    private static String textOrNull(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? null : node.asText(null);
    }

    private HttpRequest.Builder baseRequest(String baseUrl, String path, long userId) {
        String base = trimTrailingSlash(baseUrl);
        if (base.isBlank()) {
            throw new ToolException(ToolErrorKind.NOT_CONFIGURED, "AI 工具服务地址未配置：" + path);
        }
        URI uri;
        try {
            uri = URI.create(base + path);
        } catch (IllegalArgumentException exception) {
            throw new ToolException(ToolErrorKind.NOT_CONFIGURED, "AI 工具服务地址不合法：" + path, exception);
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(uri)
                .header(properties.getInternalHeader(), properties.getInternalToken())
                .header(properties.getUserIdHeader(), Long.toString(userId))
                .header(properties.getRequestIdHeader(), currentRequestId());
        String sessionId = TraceContext.sessionId();
        if (sessionId != null && !sessionId.isBlank()) {
            builder.header(properties.getSessionIdHeader(), sessionId);
        }
        return builder;
    }

    /**
     * 透传 oc-app 侧的请求 ID，使工具服务日志可与上游调用串联；缺失时回退为随机值。
     */
    private static String currentRequestId() {
        String requestId = TraceContext.requestId();
        return requestId == null || requestId.isBlank()
                ? java.util.UUID.randomUUID().toString()
                : requestId;
    }

    private String send(HttpRequest request) {
        ToolException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return response.body();
                }
                last = mapUpstream(response.statusCode(), response.body());
            } catch (IOException exception) {
                last = new ToolException(ToolErrorKind.UPSTREAM_FAILED, "工具服务调用失败", exception);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new ToolException(ToolErrorKind.UPSTREAM_FAILED, "工具服务调用被中断", exception);
            }
            if (!last.kind().retryable() || attempt == MAX_ATTEMPTS) {
                throw last;
            }
            pauseBeforeRetry("工具", last.kind(), attempt);
        }
        throw last;
    }

    private static void pauseBeforeRetry(String label, ToolErrorKind kind, int attempt) {
        log.info("{}调用可重试失败，退避重试：kind={} attempt={}", label, kind, attempt);
        try {
            Thread.sleep(RETRY_BACKOFF_MS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ToolException(ToolErrorKind.UPSTREAM_FAILED, "重试等待被中断", exception);
        }
    }

    private ToolException mapUpstream(int status, String body) {
        log.warn("工具服务返回非 2xx：status={} body={}", status, truncate(body));
        ToolErrorKind declared = kindFromBody(objectMapper, body);
        ToolErrorKind kind = declared != null
                ? declared
                : switch (status) {
                    case 400 -> ToolErrorKind.INVALID_INPUT;
                    case 402 -> ToolErrorKind.INSUFFICIENT_BALANCE;
                    case 403 -> ToolErrorKind.BLOCKED;
                    case 404 -> ToolErrorKind.NO_CONTENT;
                    case 413 -> ToolErrorKind.TOO_LARGE;
                    case 422 -> ToolErrorKind.CONTENT_REJECTED;
                    case 429 -> ToolErrorKind.UPSTREAM_BUSY;
                    case 504 -> ToolErrorKind.UPSTREAM_TIMEOUT;
                    default -> ToolErrorKind.UPSTREAM_FAILED;
                };
        return new ToolException(kind, "工具服务返回 " + status, null, status);
    }

    /**
     * 工具服务的错误体带稳定 {@code code}（与 ToolErrorKind 同名）；优先用它，避免状态码丢语义。
     */
    static ToolErrorKind kindFromBody(ObjectMapper mapper, String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode root = mapper.readTree(body);
            String code = root.path("code").asText("");
            return code.isBlank() ? null : ToolErrorKind.valueOf(code);
        } catch (JacksonException | IllegalArgumentException exception) {
            return null;
        }
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= MAX_ERROR_BODY ? value : value.substring(0, MAX_ERROR_BODY);
    }

    /**
     * TTS 音频结果；cached 表示命中工具服务缓存。
     */
    public record AudioResult(byte[] audio, String contentType, boolean cached) {}
}
