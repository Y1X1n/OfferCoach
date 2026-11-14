package com.offercoach.interview.jd.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offercoach.ai.tools.api.ToolDtos;
import com.offercoach.ai.tools.config.ToolsProperties;
import com.offercoach.ai.tools.error.ToolErrorKind;
import com.offercoach.ai.tools.error.ToolException;
import com.offercoach.ai.tools.gateway.AiToolGateway;
import com.offercoach.ai.tools.validation.FetchUrlRules;
import com.offercoach.common.cache.RedisKeys;
import com.offercoach.common.error.BizException;
import com.offercoach.common.error.ErrorCode;
import com.offercoach.interview.ai.AiCallLogger;
import com.offercoach.interview.document.WebContentCleaner;
import com.offercoach.interview.jd.domain.JobDescription;
import com.offercoach.interview.support.JsonCodec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 目标岗位 JD 的联网获取编排：URL 优先（Fetch）→ Search 兜底。
 *
 * <p>业务流程与判定规则见 docs/design/ai-jd-web-prd.md §4.3。本类只做编排与缓存，
 * 正文清理见 {@link WebContentCleaner}，JD 抽取与"抓不到"判定见 {@link JdBodyExtractor}。</p>
 */
@Service
public class JdWebImportService {

    private static final Logger log = LoggerFactory.getLogger(JdWebImportService.class);

    private static final Duration SEARCH_CACHE_TTL = Duration.ofMinutes(10);
    private static final Duration FETCH_CACHE_TTL = Duration.ofHours(1);
    private static final Duration PREVIEW_TTL = Duration.ofMinutes(15);
    private static final int MAX_RESULTS = 10;
    private static final int MAX_TITLE_LENGTH = 160;
    private static final double EXACT_MATCH_BONUS = 10.0;
    private static final double AUTO_PICK_SCORE = 1.5;
    private static final int AUTO_PICK_AUTHORITY = 3;
    private static final double AUTO_PICK_LEAD = 0.3;

    private final AiToolGateway toolGateway;
    private final JdService jdService;
    private final JdStructurer jdStructurer;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final JsonCodec jsonCodec;
    private final ToolsProperties properties;
    private final AiCallLogger aiCallLogger;

    public JdWebImportService(
            AiToolGateway toolGateway,
            JdService jdService,
            JdStructurer jdStructurer,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            JsonCodec jsonCodec,
            ToolsProperties properties,
            AiCallLogger aiCallLogger) {
        this.toolGateway = toolGateway;
        this.jdService = jdService;
        this.jdStructurer = jdStructurer;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.jsonCodec = jsonCodec;
        this.properties = properties;
        this.aiCallLogger = aiCallLogger;
    }

    /**
     * 关键词检索岗位候选；正文缓存到 Redis，供预览与导入复用，避免重复计费。
     */
    public SearchOutcome search(long userId, String query, String location, Integer count) {
        String keywords = query == null ? "" : query.strip();
        if (keywords.length() < 2 || keywords.length() > 128) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of("query", "关键词长度需为 2-128")));
        }
        String fullQuery = buildQuery(location, keywords);
        int size = Math.min(count == null ? 8 : count, MAX_RESULTS);
        List<ToolDtos.SearchResult> results;
        try {
            results = runSearch(userId, fullQuery, size);
        } catch (ToolException exception) {
            throw toBizFailure(exception, "联网搜索暂不可用");
        }

        String token = newToken("st");
        try {
            redisTemplate.opsForValue().set(RedisKeys.jdSearchCache(token), jsonCodec.write(results), SEARCH_CACHE_TTL);
        } catch (RuntimeException exception) {
            log.warn("候选缓存写入失败，预览将回源：reason={}", exception.getClass().getSimpleName());
        }
        return new SearchOutcome(token, results);
    }

    /**
     * 生成未落库的 JD 预览。URL 模式下内建 Fetch → Search 兜底。
     */
    public PreviewOutcome preview(long userId, PreviewCommand command) {
        if (command.url() != null && !command.url().isBlank()) {
            return previewFromUrl(userId, command);
        }
        if (command.searchToken() != null && command.resultId() != null) {
            return previewFromCandidate(userId, command.searchToken(), command.resultId());
        }
        throw new BizException(ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of("url", "请提供链接或选择一个候选岗位")));
    }

    private PreviewOutcome previewFromUrl(long userId, PreviewCommand command) {
        String url;
        try {
            url = FetchUrlRules.requireValid(command.url());
        } catch (IllegalArgumentException exception) {
            throw new BizException(
                    ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of("url", exception.getMessage())));
        }
        FetchOutcome fetched = fetchCleaned(userId, url);
        if (fetched.cleaned() == null || !JdBodyExtractor.looksLikeJd(fetched.cleaned())) {
            String reason = fetched.cleaned() == null ? fetched.reason() : "FETCH_TOO_SHORT";
            Optional<PreviewOutcome> fallback = searchFallback(userId, url, command.keywords(), reason);
            if (fallback.isPresent()) {
                return fallback.get();
            }
            throw new BizException(
                    ErrorCode.PARSE_NEED_MANUAL, Map.of("reason", reason, "message", "该链接与搜索均未取到 JD 正文，请手动粘贴"));
        }
        String body = JdBodyExtractor.extract(fetched.cleaned());
        return buildPreview(userId, body, fetched.title(), url, JobDescription.SOURCE_URL, hostOf(url), null, "URL");
    }

    private PreviewOutcome previewFromCandidate(long userId, String searchToken, String resultId) {
        List<ToolDtos.SearchResult> cached = readSearchCache(searchToken);
        ToolDtos.SearchResult hit = cached.stream()
                .filter(item -> resultId.equals(resultIdOf(item)))
                .findFirst()
                .orElseThrow(() -> new BizException(
                        ErrorCode.STATE_CONFLICT, Map.of("reason", "PREVIEW_EXPIRED", "message", "候选已过期，请重新搜索")));
        String cleaned = WebContentCleaner.clean(hit.content() == null ? "" : hit.content());
        if (!JdBodyExtractor.looksLikeJd(cleaned)) {
            throw new BizException(
                    ErrorCode.PARSE_NEED_MANUAL,
                    Map.of("reason", "SEARCH_CONTENT_INSUFFICIENT", "message", "该候选正文不足，请换一条"));
        }
        return buildPreview(
                userId,
                JdBodyExtractor.extract(cleaned),
                hit.title(),
                hit.url(),
                JobDescription.SOURCE_SEARCH,
                hit.siteName(),
                null,
                "SEARCH");
    }

    private Optional<PreviewOutcome> searchFallback(long userId, String url, String keywords, String reason) {
        String query = fallbackQuery(url, keywords);
        List<ToolDtos.SearchResult> results;
        try {
            results = runSearch(userId, query, MAX_RESULTS);
        } catch (BizException exception) {
            log.warn("搜索兜底失败：reason={}", exception.getMessage());
            return Optional.empty();
        } catch (ToolException exception) {
            if (exception.kind() == ToolErrorKind.NOT_CONFIGURED) {
                throw toBizFailure(exception, "联网搜索暂不可用");
            }
            log.warn("搜索兜底失败：kind={}", exception.kind());
            return Optional.empty();
        }
        if (results.isEmpty()) {
            return Optional.empty();
        }
        String token = newToken("st");
        try {
            redisTemplate.opsForValue().set(RedisKeys.jdSearchCache(token), jsonCodec.write(results), SEARCH_CACHE_TTL);
        } catch (RuntimeException exception) {
            log.warn("兜底候选缓存写入失败：reason={}", exception.getClass().getSimpleName());
        }
        ToolDtos.SearchResult best = pickBest(results, url);
        if (best == null) {
            return Optional.of(PreviewOutcome.needsChoice(token, results, reason));
        }
        String cleaned = WebContentCleaner.clean(best.content() == null ? "" : best.content());
        if (!JdBodyExtractor.looksLikeJd(cleaned)) {
            return Optional.of(PreviewOutcome.needsChoice(token, results, reason));
        }
        return Optional.of(buildPreview(
                userId,
                JdBodyExtractor.extract(cleaned),
                best.title(),
                best.url(),
                JobDescription.SOURCE_SEARCH,
                best.siteName(),
                reason,
                "SEARCH_FALLBACK"));
    }

    /**
     * 择优：URL 完全相同优先；否则分数与权威度达标且明显领先时自动采用；否则交用户选。
     */
    private ToolDtos.SearchResult pickBest(List<ToolDtos.SearchResult> results, String url) {
        for (ToolDtos.SearchResult item : results) {
            if (item.url() != null && item.url().equalsIgnoreCase(url)) {
                return item;
            }
        }
        List<ToolDtos.SearchResult> qualified = results.stream()
                .filter(item -> item.rerankScore() >= AUTO_PICK_SCORE && item.authority() >= AUTO_PICK_AUTHORITY)
                .sorted((a, b) -> Double.compare(b.rerankScore(), a.rerankScore()))
                .toList();
        if (qualified.isEmpty()) {
            return null;
        }
        if (qualified.size() == 1) {
            return qualified.get(0);
        }
        ToolDtos.SearchResult first = qualified.get(0);
        ToolDtos.SearchResult second = qualified.get(1);
        return first.rerankScore() - second.rerankScore() >= AUTO_PICK_LEAD ? first : null;
    }

    private PreviewOutcome buildPreview(
            long userId,
            String rawText,
            String rawTitle,
            String sourceUrl,
            String sourceType,
            String siteName,
            String fallbackReason,
            String resolvedBy) {
        if (rawText.length() < JdBodyExtractor.MIN_USEFUL_LENGTH) {
            throw new BizException(
                    ErrorCode.PARSE_NEED_MANUAL, Map.of("reason", "CONTENT_TOO_SHORT", "message", "取到的正文过短，请手动粘贴"));
        }
        String title = rawTitle == null || rawTitle.isBlank()
                ? JdBodyExtractor.firstNonBlankLine(rawText, MAX_TITLE_LENGTH)
                : rawTitle.strip();
        JdStructurer.StructuredJd structured = jdStructurer.structure(title, null, rawText);
        String structuredTitle = structured.structured().get("title") instanceof String value && !value.isBlank()
                ? value.strip()
                : title;
        String displayTitle = preferJobTitle(structuredTitle, title);
        StructuredPreview preview = new StructuredPreview(
                displayTitle, blankToNull(siteName), sourceUrl, sourceType, fallbackReason, rawText, structured.json());
        String token = newToken("pv");
        try {
            redisTemplate.opsForValue().set(RedisKeys.jdPreview(token), jsonCodec.write(preview), PREVIEW_TTL);
        } catch (RuntimeException exception) {
            log.warn("预览缓存写入失败：reason={}", exception.getClass().getSimpleName());
        }
        return PreviewOutcome.preview(token, preview, structured.structured(), resolvedBy);
    }

    /**
     * 由预览落库为 JD；供创建面试时在事务内调用。
     */
    public long materialize(long userId, String previewToken) {
        String json;
        try {
            json = redisTemplate.opsForValue().get(RedisKeys.jdPreview(previewToken));
        } catch (RuntimeException exception) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, Map.of("reason", "PREVIEW_UNAVAILABLE"));
        }
        if (json == null) {
            throw new BizException(
                    ErrorCode.STATE_CONFLICT, Map.of("reason", "PREVIEW_EXPIRED", "message", "预览已过期，请重新获取"));
        }
        StructuredPreview preview = readValue(json, new TypeReference<StructuredPreview>() {});
        JdService.JdView view = jdService.createWithStructured(
                userId,
                preview.title(),
                null,
                preview.rawText(),
                preview.structuredJson(),
                preview.sourceType(),
                preview.sourceUrl());
        return view.id();
    }

    private FetchOutcome fetchCleaned(long userId, String url) {
        String key = RedisKeys.jdFetchCache(digest(url));
        try {
            String cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                aiCallLogger.log(
                        userId, "FETCH", "offercoach-cache", "cache", "DEGRADED", null, null, null, 0, "CACHE_HIT");
                return cached.isEmpty()
                        ? new FetchOutcome(null, null, "FETCH_BLOCKED")
                        : new FetchOutcome(cached, null, null);
            }
        } catch (RuntimeException exception) {
            log.warn("抓取缓存读取失败：reason={}", exception.getClass().getSimpleName());
        }
        enforceDailyLimit(
                userId, RedisKeys.dailyFetch(userId, LocalDate.now().toString()), properties.getFetchDailyLimit());
        String cleaned;
        String pageTitle;
        try {
            ToolDtos.FetchResponse response = toolGateway.fetch(userId, url);
            cleaned = WebContentCleaner.clean(response.content() == null ? "" : response.content());
            pageTitle = response.title();
        } catch (ToolException exception) {
            log.info("抓取未成功，转入搜索兜底：kind={}", exception.kind());
            String reason =
                    switch (exception.kind()) {
                        case BLOCKED -> "FETCH_BLOCKED";
                        case NO_CONTENT -> "FETCH_NO_CONTENT";
                        default -> "FETCH_FAILED";
                    };
            cacheQuietly(key, "", FETCH_CACHE_TTL);
            return new FetchOutcome(null, null, reason);
        }
        cacheQuietly(key, cleaned, FETCH_CACHE_TTL);
        return new FetchOutcome(cleaned, pageTitle, null);
    }

    private List<ToolDtos.SearchResult> runSearch(long userId, String query, int count) {
        String cacheKey = RedisKeys.jdSearchQuery(digest(query + "|" + count));
        long startedAt = System.nanoTime();
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                aiCallLogger.log(
                        userId,
                        "SEARCH",
                        "offercoach-cache",
                        "cache",
                        "DEGRADED",
                        null,
                        null,
                        null,
                        (System.nanoTime() - startedAt) / 1_000_000,
                        "CACHE_HIT");
                return readValue(cached, new TypeReference<List<ToolDtos.SearchResult>>() {});
            }
        } catch (RuntimeException exception) {
            log.warn("检索缓存读取失败：reason={}", exception.getClass().getSimpleName());
        }
        enforceDailyLimit(
                userId, RedisKeys.dailySearch(userId, LocalDate.now().toString()), properties.getSearchDailyLimit());
        List<ToolDtos.SearchResult> results = toolGateway.search(userId, query, count, null, true);
        cacheQuietly(cacheKey, jsonCodec.write(results), SEARCH_CACHE_TTL);
        return results;
    }

    private List<ToolDtos.SearchResult> readSearchCache(String token) {
        String json = redisTemplate.opsForValue().get(RedisKeys.jdSearchCache(token));
        if (json == null) {
            throw new BizException(
                    ErrorCode.STATE_CONFLICT, Map.of("reason", "SEARCH_EXPIRED", "message", "候选已过期，请重新搜索"));
        }
        return readValue(json, new TypeReference<List<ToolDtos.SearchResult>>() {});
    }

    private <T> T readValue(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, Map.of("reason", "CACHE_CORRUPTED"));
        }
    }

    /**
     * 抓取结果：cleaned 非空表示抓到正文，否则 reason 说明失败原因。
     */
    private record FetchOutcome(String cleaned, String title, String reason) {}

    private void cacheQuietly(String key, String value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, value, ttl);
        } catch (RuntimeException exception) {
            log.warn("缓存写入失败：reason={}", exception.getClass().getSimpleName());
        }
    }

    private void enforceDailyLimit(long userId, String key, int limit) {
        if (limit <= 0) {
            return;
        }
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, Duration.ofDays(1));
            }
            if (count != null && count > limit) {
                throw new BizException(ErrorCode.RATE_LIMITED, Map.of("limit", limit));
            }
        } catch (BizException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn("限额 Redis 不可用，放行：reason={}", exception.getClass().getSimpleName());
        }
    }

    /**
     * 把工具异常映射为对外业务错误：上游不可用/超时/关闭统一为 503。
     */
    private static BizException toBizFailure(ToolException exception, String fallbackMessage) {
        return switch (exception.kind()) {
            case NOT_CONFIGURED, UPSTREAM_BUSY, UPSTREAM_TIMEOUT, UPSTREAM_FAILED, BUSY -> new BizException(
                    ErrorCode.AI_UNAVAILABLE,
                    Map.of(
                            "reason", exception.kind().name(),
                            "retryable", exception.kind().retryable(),
                            "message", fallbackMessage));
            default -> new BizException(
                    ErrorCode.INTERNAL_ERROR, Map.of("reason", exception.kind().name()));
        };
    }

    private static String buildQuery(String location, String keywords) {
        String loc = location == null ? "" : location.strip();
        if (loc.isEmpty()) {
            return keywords;
        }
        if (keywords.contains(loc)) {
            return keywords;
        }
        return (loc + " " + keywords).strip();
    }

    /**
     * 搜索兜底检索词：用户关键词优先；否则 site:host + 路径 slug；再否则 site:host 招聘。
     */
    static String fallbackQuery(String url, String keywords) {
        if (keywords != null && !keywords.isBlank()) {
            return keywords.strip();
        }
        String host = hostOf(url);
        String slug = slugOf(url);
        if (host == null) {
            return "招聘";
        }
        if (slug != null && !slug.isBlank()) {
            return "site:" + host + " " + slug + " 招聘";
        }
        return "site:" + host + " 招聘";
    }

    private static String hostOf(String url) {
        try {
            String host = java.net.URI.create(url).getHost();
            return host == null || host.isBlank() ? null : host.toLowerCase(Locale.ROOT);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /**
     * 从 URL 路径提取可能的岗位名（去掉数字、扩展名与连字符）。
     */
    private static String slugOf(String url) {
        try {
            String path = java.net.URI.create(url).getPath();
            if (path == null || path.isBlank()) {
                return null;
            }
            String last = path;
            int slash = last.lastIndexOf('/');
            if (slash >= 0 && slash < last.length() - 1) {
                last = last.substring(slash + 1);
            } else {
                String[] parts = path.split("/");
                last = parts.length == 0 ? "" : parts[parts.length - 1];
            }
            String candidate = last.replaceAll("\\.[a-zA-Z0-9]{2,5}$", "")
                    .replaceAll("[_\\-+]+", " ")
                    .strip();
            // 纯数字或过短视为无信息
            if (candidate.length() < 2 || candidate.matches("[0-9 ]+")) {
                return null;
            }
            return candidate.length() > 60 ? candidate.substring(0, 60) : candidate;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    public static String resultIdOf(ToolDtos.SearchResult item) {
        return digest(item.url() == null ? "" : item.url());
    }

    /**
     * 页面标题常是"XX招聘系统--招聘明细"，比结构化出的岗位名差；优先取像岗位名的那个。
     */
    static String preferJobTitle(String structuredTitle, String pageTitle) {
        if (structuredTitle != null && !structuredTitle.isBlank() && structuredTitle.length() <= MAX_TITLE_LENGTH) {
            return structuredTitle.strip();
        }
        return pageTitle;
    }

    private static String newToken(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
    }

    private static String digest(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    /**
     * 预览请求。
     */
    public record PreviewCommand(String url, String keywords, String searchToken, String resultId) {}

    /**
     * 落库所需的预览数据。
     */
    public record StructuredPreview(
            String title,
            String siteName,
            String sourceUrl,
            String sourceType,
            String fallbackReason,
            String rawText,
            String structuredJson) {}

    /**
     * 预览结果；needsChoice 为真时提示前端展示候选。
     */
    public record PreviewOutcome(
            boolean needsChoice,
            String previewToken,
            String searchToken,
            String resolvedBy,
            String fallbackReason,
            String title,
            String company,
            String siteName,
            String sourceUrl,
            String rawText,
            Map<String, Object> structured,
            List<Candidate> candidates) {

        static PreviewOutcome preview(
                String token, StructuredPreview preview, Map<String, Object> structured, String resolvedBy) {
            return new PreviewOutcome(
                    false,
                    token,
                    null,
                    resolvedBy,
                    preview.fallbackReason(),
                    preview.title(),
                    null,
                    preview.siteName(),
                    preview.sourceUrl(),
                    preview.rawText(),
                    structured,
                    List.of());
        }

        static PreviewOutcome needsChoice(
                String searchToken, List<ToolDtos.SearchResult> results, String fallbackReason) {
            return new PreviewOutcome(
                    true,
                    null,
                    searchToken,
                    "SEARCH_FALLBACK",
                    fallbackReason,
                    null,
                    null,
                    null,
                    null,
                    null,
                    Map.of(),
                    toCandidates(results));
        }

        private static List<Candidate> toCandidates(List<ToolDtos.SearchResult> results) {
            List<Candidate> list = new ArrayList<>();
            for (ToolDtos.SearchResult item : results) {
                list.add(new Candidate(
                        resultIdOf(item),
                        item.title(),
                        item.url(),
                        item.siteName(),
                        item.snippet(),
                        item.rerankScore()));
            }
            return list;
        }
    }

    /**
     * 候选岗位（不含正文）。
     */
    public record Candidate(
            String resultId, String title, String url, String siteName, String snippet, double rerankScore) {}

    /**
     * 搜索输出。
     */
    public record SearchOutcome(String searchToken, List<ToolDtos.SearchResult> results) {}
}
