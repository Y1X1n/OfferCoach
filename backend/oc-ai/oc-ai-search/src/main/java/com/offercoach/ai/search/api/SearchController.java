package com.offercoach.ai.search.api;

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
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部检索端点：关键词 → 结构化结果。只做检索，不做岗位语义判断。
 */
@RestController
@RequestMapping("/internal/v1/search")
public class SearchController {

    private static final Logger log = LoggerFactory.getLogger(SearchController.class);
    private static final String SCENE = "SEARCH";
    private static final String MODEL = "tools-search";
    private static final int MAX_QUERY_CHARS = 128;
    private static final int OFFICIAL_MAX_COUNT = 50;

    private final DeepSeekToolsClient client;
    private final InternalAuth internalAuth;
    private final ToolCallLogger callLogger;
    private final ObjectMapper objectMapper;
    private final AiRuntimeConfigService runtimeConfig;
    private final ToolsProperties properties;
    private final ConcurrencyLimiter limiter;

    public SearchController(
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
        this.properties = properties;
        this.limiter = new ConcurrencyLimiter("search", properties.getSearchMaxConcurrency());
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ToolDtos.SearchResponse search(@RequestBody ToolDtos.SearchRequest body, HttpServletRequest request) {
        long userId = internalAuth.requireUser(request);
        if (!runtimeConfig.tools().searchEnabled()) {
            throw new ToolException(ToolErrorKind.NOT_CONFIGURED, "联网搜索已被管理员关闭");
        }
        String query = body.query() == null ? "" : body.query().strip();
        if (query.isEmpty()) {
            throw new ToolException(ToolErrorKind.INVALID_INPUT, "检索词不能为空");
        }
        if (query.length() > MAX_QUERY_CHARS) {
            throw new ToolException(ToolErrorKind.INVALID_INPUT, "检索词超过 " + MAX_QUERY_CHARS + " 字符");
        }
        int count = body.count() == null ? 10 : body.count();
        if (count < 1 || count > OFFICIAL_MAX_COUNT) {
            throw new ToolException(ToolErrorKind.INVALID_INPUT, "count 需为 1-" + OFFICIAL_MAX_COUNT);
        }
        if (count > properties.getSearchMaxCount()) {
            count = properties.getSearchMaxCount();
        }
        String oriQuestion = trimToNull(body.oriQuestion());
        Boolean needContent = body.needContent() == null ? Boolean.FALSE : body.needContent();
        // localization（IP）默认不下发，避免把用户 IP 交给第三方；location 由调用方写进 query 关键词。
        String localization = trimToNull(body.localization());

        long startedAt = System.nanoTime();
        try (ConcurrencyLimiter.Permit ignored = limiter.acquire()) {
            String json = client.search(query, count, oriQuestion, needContent, localization);
            ToolDtos.SearchResponse response = parse(json);
            long latencyMs = (System.nanoTime() - startedAt) / 1_000_000;
            callLogger.success(userId, SCENE, MODEL, latencyMs, null, null);
            return response;
        } catch (ToolException exception) {
            long latencyMs = (System.nanoTime() - startedAt) / 1_000_000;
            callLogger.failed(userId, SCENE, MODEL, latencyMs, exception.kind().name());
            log.warn("联网检索失败：kind={} reason={}", exception.kind(), exception.getMessage());
            throw exception;
        }
    }

    private ToolDtos.SearchResponse parse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root.has("error")) {
                throw new ToolException(
                        ToolErrorKind.UPSTREAM_FAILED,
                        root.path("error").path("message").asText("检索失败"));
            }
            List<ToolDtos.SearchResult> results = new ArrayList<>();
            for (JsonNode item : root.path("results")) {
                results.add(new ToolDtos.SearchResult(
                        item.path("rerank_score").asDouble(0),
                        item.path("title").asText(""),
                        item.path("url").asText(""),
                        item.path("snippet").asText(""),
                        item.path("summary").asText(""),
                        item.path("content").asText(""),
                        item.path("site_name").isNull()
                                ? null
                                : item.path("site_name").asText(null),
                        item.path("site_icon").isNull()
                                ? null
                                : item.path("site_icon").asText(null),
                        item.path("date").isNull() ? null : item.path("date").asText(null),
                        item.path("authority").asInt(-1)));
            }
            return new ToolDtos.SearchResponse(results);
        } catch (IOException exception) {
            throw new ToolException(ToolErrorKind.UPSTREAM_FAILED, "检索结果解析失败", exception);
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
