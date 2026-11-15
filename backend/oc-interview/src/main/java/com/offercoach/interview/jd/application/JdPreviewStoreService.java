package com.offercoach.interview.jd.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offercoach.common.cache.RedisKeys;
import com.offercoach.common.error.BizException;
import com.offercoach.common.error.ErrorCode;
import com.offercoach.interview.support.JsonCodec;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * JD 预览存取：未落库预览的短期缓存与"确认后落库"。
 *
 * <p>预览阶段完成结构化，创建面试时直接复用，避免二次调用模型。</p>
 */
@Service
public class JdPreviewStoreService {

    private static final Logger log = LoggerFactory.getLogger(JdPreviewStoreService.class);
    private static final Duration TTL = Duration.ofMinutes(15);
    private static final int MAX_TITLE_LENGTH = 160;

    private final JdService jdService;
    private final JdStructurer jdStructurer;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final JsonCodec jsonCodec;

    public JdPreviewStoreService(
            JdService jdService,
            JdStructurer jdStructurer,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            JsonCodec jsonCodec) {
        this.jdService = jdService;
        this.jdStructurer = jdStructurer;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.jsonCodec = jsonCodec;
    }

    /**
     * 结构化并缓存预览；返回 token、预览数据与结构化结果。
     */
    public StoredPreview store(
            String rawText,
            String rawTitle,
            String sourceUrl,
            String sourceType,
            String siteName,
            String fallbackReason) {
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
        String token = "pv_" + UUID.randomUUID().toString().replace("-", "");
        try {
            redisTemplate.opsForValue().set(RedisKeys.jdPreview(token), jsonCodec.write(preview), TTL);
        } catch (RuntimeException exception) {
            log.warn("预览缓存写入失败：reason={}", exception.getClass().getSimpleName());
        }
        return new StoredPreview(token, preview, structured.structured());
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
        StructuredPreview preview;
        try {
            preview = objectMapper.readValue(json, new TypeReference<StructuredPreview>() {});
        } catch (JsonProcessingException exception) {
            throw new BizException(
                    ErrorCode.STATE_CONFLICT, Map.of("reason", "PREVIEW_EXPIRED", "message", "预览已过期，请重新获取"));
        }
        JdService.JdView view = jdService.createWithStructured(
                userId,
                preview.title(),
                null,
                preview.rawText(),
                preview.structuredJson(),
                preview.sourceType(),
                preview.sourceUrl());
        deleteAfterCommit(previewToken);
        return view.id();
    }

    /**
     * 预览令牌一次性：提交成功后删除，避免同一令牌反复建会话；失败回滚时保留。
     */
    private void deleteAfterCommit(String previewToken) {
        Runnable delete = () -> {
            try {
                redisTemplate.delete(RedisKeys.jdPreview(previewToken));
            } catch (RuntimeException exception) {
                log.warn("预览令牌删除失败：reason={}", exception.getClass().getSimpleName());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    delete.run();
                }
            });
        } else {
            delete.run();
        }
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

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

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
     * 预览结果：token + 预览数据 + 结构化字段。
     */
    public record StoredPreview(String token, StructuredPreview preview, Map<String, Object> structured) {}
}
