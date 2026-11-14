package com.offercoach.interview.api.dto.jd;

import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

/**
 * 联网获取目标岗位 JD 的请求与响应。
 */
public final class JdWebDtos {

    private JdWebDtos() {}

    public record JdSearchRequest(
            @Size(max = 128, message = "关键词最多 128 字") String query,
            @Size(max = 64, message = "城市最多 64 字") String location,
            Integer count) {}

    public record JdSearchResponse(String searchToken, List<Candidate> results) {}

    public record Candidate(
            String resultId,
            String title,
            String url,
            String siteName,
            String snippet,
            String summary,
            double rerankScore,
            int authority) {}

    /**
     * 预览请求：URL 模式传 url（可选 keywords 供兜底），候选模式传 searchToken + resultId。
     */
    public record JdPreviewRequest(
            @Size(max = 8192, message = "链接过长") String url,
            @Size(max = 128, message = "关键词最多 128 字") String keywords,
            String searchToken,
            String resultId) {}

    /**
     * 预览响应；needsChoice 为真时 previewToken 为空、candidates 非空。
     */
    public record JdPreviewResponse(
            boolean needsChoice,
            String previewToken,
            String searchToken,
            String resolvedBy,
            String fallbackReason,
            String title,
            String siteName,
            String sourceUrl,
            String rawText,
            Map<String, Object> structured,
            List<Candidate> candidates) {}
}
