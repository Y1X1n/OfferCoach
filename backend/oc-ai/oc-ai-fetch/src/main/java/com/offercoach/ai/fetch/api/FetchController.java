package com.offercoach.ai.fetch.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offercoach.ai.AiRuntimeConfigService;
import com.offercoach.ai.tools.api.ToolDtos;
import com.offercoach.ai.tools.client.DeepSeekToolsClient;
import com.offercoach.ai.tools.concurrent.ConcurrencyLimiter;
import com.offercoach.ai.tools.config.ToolsProperties;
import com.offercoach.ai.tools.error.ToolErrorKind;
import com.offercoach.ai.tools.error.ToolException;
import com.offercoach.ai.tools.internal.InternalAuth;
import com.offercoach.ai.tools.log.ToolCallLogger;
import com.offercoach.ai.tools.validation.FetchUrlRules;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部抓取端点：URL → Markdown 正文。
 *
 * <p>URL 合法性在此再做一层纵深防御（协议、host、无用户信息），避免被当作开放代理。</p>
 */
@RestController
@RequestMapping("/internal/v1/fetch")
public class FetchController {

    private static final Logger log = LoggerFactory.getLogger(FetchController.class);
    private static final String SCENE = "FETCH";
    private static final String MODEL = "tools-fetch";
    private static final int MAX_URL_LENGTH = 8192;

    private final DeepSeekToolsClient client;
    private final InternalAuth internalAuth;
    private final ToolCallLogger callLogger;
    private final ObjectMapper objectMapper;
    private final AiRuntimeConfigService runtimeConfig;
    private final ConcurrencyLimiter limiter;

    public FetchController(
            DeepSeekToolsClient client,
            InternalAuth internalAuth,
            ToolCallLogger callLogger,
            ObjectMapper objectMapper,
            AiRuntimeConfigService runtimeConfig,
            ToolsProperties properties) {
        this.client = client;
        this.internalAuth = internalAuth;
        this.callLogger = callLogger;
        this.objectMapper = objectMapper;
        this.runtimeConfig = runtimeConfig;
        this.limiter = new ConcurrencyLimiter("fetch", properties.getFetchMaxConcurrency());
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ToolDtos.FetchResponse fetch(@RequestBody ToolDtos.FetchRequest body, HttpServletRequest request) {
        long userId = internalAuth.requireUser(request);
        if (!runtimeConfig.tools().fetchEnabled()) {
            throw new ToolException(ToolErrorKind.NOT_CONFIGURED, "网页抓取已被管理员关闭");
        }
        String url = FetchUrlRules.requireValid(body.url());

        long startedAt = System.nanoTime();
        try (ConcurrencyLimiter.Permit ignored = limiter.acquire()) {
            String json = client.fetch(url);
            ToolDtos.FetchResponse response = parse(json);
            long latencyMs = (System.nanoTime() - startedAt) / 1_000_000;
            callLogger.success(userId, SCENE, MODEL, latencyMs, null, null);
            return response;
        } catch (ToolException exception) {
            long latencyMs = (System.nanoTime() - startedAt) / 1_000_000;
            callLogger.failed(userId, SCENE, MODEL, latencyMs, exception.kind().name());
            log.warn("网页抓取失败：kind={} reason={}", exception.kind(), exception.getMessage());
            throw exception;
        }
    }

    private ToolDtos.FetchResponse parse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root.has("error")) {
                throw new ToolException(
                        ToolErrorKind.UPSTREAM_FAILED,
                        root.path("error").path("message").asText("抓取失败"));
            }
            String content = root.path("content").asText("");
            if (content.isBlank()) {
                throw new ToolException(ToolErrorKind.NO_CONTENT);
            }
            String title = root.path("title").isNull() ? "" : root.path("title").asText("");
            return new ToolDtos.FetchResponse(title, content);
        } catch (IOException exception) {
            throw new ToolException(ToolErrorKind.UPSTREAM_FAILED, "抓取结果解析失败", exception);
        }
    }
}
