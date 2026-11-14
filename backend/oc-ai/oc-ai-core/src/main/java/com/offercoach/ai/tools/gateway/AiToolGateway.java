package com.offercoach.ai.tools.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.offercoach.ai.tools.api.ToolDtos;
import com.offercoach.ai.tools.config.ToolsProperties;
import com.offercoach.ai.tools.error.ToolErrorKind;
import com.offercoach.ai.tools.error.ToolException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 调用三个 AI 工具微服务的客户端；oc-app 是它们唯一的调用方。
 *
 * <p>工具服务只做「文件 ↔ 文本 / 音频」转换，鉴权、额度与降级决策留在本侧。</p>
 */
@Component
public class AiToolGateway {

    private static final Logger log = LoggerFactory.getLogger(AiToolGateway.class);
    private static final int MAX_ERROR_BODY = 512;
    private static final String BOUNDARY_PREFIX = "----ocAiGateway";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

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
        Multipart body = multipart(List.of(new Part("file", fileName, contentType, audio)));
        HttpRequest request = baseRequest(properties.getAsrBaseUrl(), "/internal/v1/asr", userId)
                .timeout(properties.getAsrTimeout())
                .header("Content-Type", body.contentType())
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.bytes()))
                .build();
        String json = send(request);
        try {
            JsonNode root = objectMapper.readTree(json);
            return new ToolDtos.AsrResult(
                    root.path("requestId").asText(""),
                    root.path("text").asText(""),
                    root.path("durationMs").isNumber() ? root.path("durationMs").asInt() : null);
        } catch (IOException exception) {
            throw new ToolException(ToolErrorKind.UPSTREAM_FAILED, "ASR 结果解析失败", exception);
        }
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
        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw mapUpstream(response.statusCode(), new String(response.body(), StandardCharsets.UTF_8));
            }
            String contentType = response.headers().firstValue("Content-Type").orElse("audio/wav");
            boolean cached = response.headers()
                    .firstValue("X-OC-Tts-Cache")
                    .map("HIT"::equalsIgnoreCase)
                    .orElse(false);
            return new AudioResult(response.body(), contentType, cached);
        } catch (IOException exception) {
            throw new ToolException(ToolErrorKind.UPSTREAM_FAILED, "TTS 服务调用失败", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ToolException(ToolErrorKind.UPSTREAM_FAILED, "TTS 服务调用被中断", exception);
        }
    }

    /**
     * 调用 OCR 服务；用于扫描件简历降级识别。
     */
    public ToolDtos.OcrResult recognize(long userId, byte[] file, String fileName, String contentType, String purpose) {
        Multipart body = multipart(List.of(
                new Part("purpose", null, null, purpose.getBytes(StandardCharsets.UTF_8)),
                new Part("file", fileName, contentType, file)));
        HttpRequest request = baseRequest(properties.getOcrBaseUrl(), "/internal/v1/ocr", userId)
                .timeout(properties.getOcrTimeout())
                .header("Content-Type", body.contentType())
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.bytes()))
                .build();
        String json = send(request);
        try {
            JsonNode root = objectMapper.readTree(json);
            List<ToolDtos.OcrPage> pages = new ArrayList<>();
            for (JsonNode page : root.path("pages")) {
                pages.add(new ToolDtos.OcrPage(
                        page.path("index").asInt(pages.size()),
                        page.path("text").asText(""),
                        page.path("finishReason").asText("stop")));
            }
            return new ToolDtos.OcrResult(
                    root.path("id").asText(""), root.path("pageCount").asInt(pages.size()), pages);
        } catch (IOException exception) {
            throw new ToolException(ToolErrorKind.UPSTREAM_FAILED, "OCR 结果解析失败", exception);
        }
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
        try {
            JsonNode root = objectMapper.readTree(json);
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
        } catch (IOException exception) {
            throw new ToolException(ToolErrorKind.UPSTREAM_FAILED, "检索结果解析失败", exception);
        }
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
        try {
            JsonNode root = objectMapper.readTree(json);
            return new ToolDtos.FetchResponse(
                    root.path("title").asText(""), root.path("content").asText(""));
        } catch (IOException exception) {
            throw new ToolException(ToolErrorKind.UPSTREAM_FAILED, "抓取结果解析失败", exception);
        }
    }

    private static String textOrNull(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? null : node.asText(null);
    }

    private HttpRequest.Builder baseRequest(String baseUrl, String path, long userId) {
        String base = baseUrl == null || baseUrl.endsWith("/") ? trimTrailingSlash(baseUrl) : baseUrl;
        return HttpRequest.newBuilder()
                .uri(URI.create(base + path))
                .header(properties.getInternalHeader(), properties.getInternalToken())
                .header(properties.getUserIdHeader(), Long.toString(userId))
                .header(properties.getRequestIdHeader(), UUID.randomUUID().toString());
    }

    private String send(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw mapUpstream(response.statusCode(), response.body());
            }
            return response.body();
        } catch (IOException exception) {
            throw new ToolException(ToolErrorKind.UPSTREAM_FAILED, "工具服务调用失败", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ToolException(ToolErrorKind.UPSTREAM_FAILED, "工具服务调用被中断", exception);
        }
    }

    private ToolException mapUpstream(int status, String body) {
        log.warn("工具服务返回非 2xx：status={} body={}", status, truncate(body));
        ToolErrorKind declared = kindFromBody(body);
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
    private ToolErrorKind kindFromBody(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            String code = root.path("code").asText("");
            return code.isBlank() ? null : ToolErrorKind.valueOf(code);
        } catch (IOException | IllegalArgumentException exception) {
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
     * TTS 音频结果；cached 表示命中工具服务缓存。
     */
    public record AudioResult(byte[] audio, String contentType, boolean cached) {}

    private record Part(String name, String fileName, String contentType, byte[] content) {}

    private record Multipart(byte[] bytes, String contentType) {}
}
