package com.offercoach.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.offercoach.ai.AiRuntimeConfigService.AiRuntimeSettings;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * OpenAI 兼容 /chat/completions 客户端；每次调用读取运行时配置，不记录密钥与完整提示词。
 */
@Component
public class OpenAiCompatibleProvider implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleProvider.class);
    private static final String PROVIDER = "openai-compatible";
    private static final int PROBE_MAX_TOKENS = 8;
    private static final double PROBE_TEMPERATURE = 0.0;

    private final AiRuntimeConfigService runtimeConfig;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OpenAiCompatibleProvider(AiRuntimeConfigService runtimeConfig, ObjectMapper objectMapper) {
        this.runtimeConfig = runtimeConfig;
        this.objectMapper = objectMapper;
        this.httpClient =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @Override
    public boolean available() {
        return usable(runtimeConfig.current());
    }

    @Override
    public AiResult complete(AiScene scene, String systemPrompt, String userPrompt) {
        AiRuntimeSettings settings = runtimeConfig.current();
        if (!usable(settings)) {
            throw new AiUnavailableException("AI 未启用或未配置");
        }
        return execute(settings, modelOf(settings, scene), systemPrompt, userPrompt, settings.timeout());
    }

    @Override
    public void stream(AiScene scene, String systemPrompt, String userPrompt, Consumer<String> onDelta) {
        AiRuntimeSettings settings = runtimeConfig.current();
        if (!usable(settings)) {
            throw new AiUnavailableException("AI 未启用或未配置");
        }
        AtomicBoolean received = new AtomicBoolean(false);
        try {
            HttpResponse<InputStream> response = httpClient.send(
                    request(
                            settings,
                            modelOf(settings, scene),
                            systemPrompt,
                            userPrompt,
                            true,
                            settings.maxTokens(),
                            settings.temperature(),
                            settings.timeout()),
                    HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                readAndDiscard(response.body());
                throw new AiUnavailableException("AI 流式请求失败：" + response.statusCode());
            }
            try (BufferedReader reader =
                    new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data:")) {
                        continue;
                    }
                    String payload = line.substring("data:".length()).trim();
                    if (payload.isEmpty()) {
                        continue;
                    }
                    if ("[DONE]".equals(payload)) {
                        break;
                    }
                    JsonNode root = objectMapper.readTree(payload);
                    JsonNode delta = root.path("choices").path(0).path("delta").path("content");
                    if (delta.isTextual() && !delta.asText().isEmpty()) {
                        received.set(true);
                        onDelta.accept(delta.asText());
                    }
                }
            }
        } catch (IOException ex) {
            throw new AiUnavailableException("AI 流式请求失败", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AiUnavailableException("AI 流式请求被中断", ex);
        }
        if (!received.get()) {
            throw new AiUnavailableException("AI 流式响应为空");
        }
    }

    /**
     * 管理端连通性探测：使用当前运行时配置发一次极小请求，超时由调用方控制。
     */
    public AiResult probe(String systemPrompt, String userPrompt, Duration timeout) {
        AiRuntimeSettings settings = runtimeConfig.current();
        if (!usable(settings)) {
            throw new AiUnavailableException("AI 未启用或未配置");
        }
        String model = settings.model();
        long startedAt = System.nanoTime();
        try {
            HttpResponse<String> response = httpClient.send(
                    request(
                            settings,
                            model,
                            systemPrompt,
                            userPrompt,
                            false,
                            PROBE_MAX_TOKENS,
                            PROBE_TEMPERATURE,
                            timeout),
                    HttpResponse.BodyHandlers.ofString());
            requireSuccess(response.statusCode(), response.body());
            JsonNode root = objectMapper.readTree(response.body());
            String text =
                    root.path("choices").path(0).path("message").path("content").asText("");
            int tokensIn = root.path("usage").path("prompt_tokens").asInt(estimateTokens(systemPrompt + userPrompt));
            int tokensOut = root.path("usage").path("completion_tokens").asInt(estimateTokens(text));
            long latencyMs = (System.nanoTime() - startedAt) / 1_000_000;
            return new AiResult(text, PROVIDER, model, tokensIn, tokensOut, latencyMs);
        } catch (IOException ex) {
            throw new AiUnavailableException("AI 请求失败", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AiUnavailableException("AI 请求被中断", ex);
        }
    }

    private AiResult execute(
            AiRuntimeSettings settings, String model, String systemPrompt, String userPrompt, Duration timeout) {
        long startedAt = System.nanoTime();
        try {
            HttpResponse<String> response = httpClient.send(
                    request(
                            settings,
                            model,
                            systemPrompt,
                            userPrompt,
                            false,
                            settings.maxTokens(),
                            settings.temperature(),
                            timeout),
                    HttpResponse.BodyHandlers.ofString());
            requireSuccess(response.statusCode(), response.body());
            JsonNode root = objectMapper.readTree(response.body());
            String text =
                    root.path("choices").path(0).path("message").path("content").asText("");
            int tokensIn = root.path("usage").path("prompt_tokens").asInt(estimateTokens(systemPrompt + userPrompt));
            int tokensOut = root.path("usage").path("completion_tokens").asInt(estimateTokens(text));
            long latencyMs = (System.nanoTime() - startedAt) / 1_000_000;
            return new AiResult(text, PROVIDER, model, tokensIn, tokensOut, latencyMs);
        } catch (IOException ex) {
            throw new AiUnavailableException("AI 请求失败", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AiUnavailableException("AI 请求被中断", ex);
        }
    }

    private HttpRequest request(
            AiRuntimeSettings settings,
            String model,
            String systemPrompt,
            String userPrompt,
            boolean stream,
            int maxTokens,
            double temperature,
            Duration timeout)
            throws IOException {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("temperature", temperature);
        body.put("max_tokens", maxTokens);
        body.put("stream", stream);
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", systemPrompt);
        messages.addObject().put("role", "user").put("content", userPrompt);
        URI uri;
        try {
            uri = URI.create(trimTrailingSlash(settings.baseUrl()) + "/chat/completions");
        } catch (IllegalArgumentException exception) {
            throw new AiUnavailableException("AI baseUrl 不合法", exception);
        }
        return HttpRequest.newBuilder()
                .uri(uri)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + settings.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
    }

    private static boolean usable(AiRuntimeSettings settings) {
        return settings != null
                && settings.enabled()
                && settings.apiKey() != null
                && !settings.apiKey().isBlank()
                && settings.baseUrl() != null
                && !settings.baseUrl().isBlank();
    }

    private static String modelOf(AiRuntimeSettings settings, AiScene scene) {
        return switch (scene) {
            case RESUME_PARSE, JD_ANALYZE, JD_MATCH -> settings.smallModel();
            default -> settings.model();
        };
    }

    private void requireSuccess(int status, String body) {
        if (status >= 200 && status < 300) {
            return;
        }
        log.warn("AI 供应商返回非 2xx：status={} body={}", status, truncate(body));
        throw new AiUnavailableException("AI 供应商返回 " + status);
    }

    private static void readAndDiscard(InputStream input) {
        try (InputStream stream = input) {
            stream.transferTo(java.io.OutputStream.nullOutputStream());
        } catch (IOException ignored) {
            // 已在读取失败路径，无需再次抛出。
        }
    }

    private static int estimateTokens(String text) {
        return text == null ? 0 : Math.max(1, text.length() / 2);
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= 300 ? value : value.substring(0, 300);
    }

    private static String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
