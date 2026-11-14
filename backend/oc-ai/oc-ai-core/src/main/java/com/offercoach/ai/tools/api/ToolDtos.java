package com.offercoach.ai.tools.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 工具服务对外 DTO；字段与 oc-app 编排层一一对应。
 */
public final class ToolDtos {

    private ToolDtos() {}

    /**
     * ASR 转写结果。
     */
    public record AsrResult(String requestId, String text, Integer durationMs) {}

    /**
     * OCR 单页结果；text 保留模型原始输出（含坐标标记）。
     */
    public record OcrPage(int index, String text, String finishReason) {}

    /**
     * OCR 整体结果；pageCount 为成功页数，用于计费与统计。
     */
    public record OcrResult(String id, int pageCount, List<OcrPage> pages) {}

    /**
     * TTS 请求参数；显式标注构造器，避免 Jackson 缺少参数名信息时反序列化失败。
     */
    public record TtsRequest(
            @JsonProperty("text") String text,
            @JsonProperty("voiceId") String voiceId,
            @JsonProperty("format") String format) {

        @JsonCreator
        public TtsRequest {}
    }

    /**
     * 统一错误体。
     */
    public record ToolErrorBody(String code, String message, boolean retryable) {}

    /**
     * Search 请求参数。
     */
    public record SearchRequest(
            @JsonProperty("query") String query,
            @JsonProperty("count") Integer count,
            @JsonProperty("oriQuestion") String oriQuestion,
            @JsonProperty("needContent") Boolean needContent,
            @JsonProperty("localization") String localization) {

        @JsonCreator
        public SearchRequest {}
    }

    /**
     * Search 单条结果；content 仅在 needContent 时非空。
     */
    public record SearchResult(
            double rerankScore,
            String title,
            String url,
            String snippet,
            String summary,
            String content,
            String siteName,
            String siteIcon,
            String date,
            int authority) {}

    /**
     * Search 响应。
     */
    public record SearchResponse(List<SearchResult> results) {}

    /**
     * Fetch 请求参数。
     */
    public record FetchRequest(@JsonProperty("url") String url) {

        @JsonCreator
        public FetchRequest {}
    }

    /**
     * Fetch 结果；content 为 Markdown 正文。
     */
    public record FetchResponse(String title, String content) {}
}
