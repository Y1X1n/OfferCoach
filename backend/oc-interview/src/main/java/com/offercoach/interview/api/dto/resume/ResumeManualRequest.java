package com.offercoach.interview.api.dto.resume;

import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * 手动新建简历请求：rawText 与 structured 至少提供一项。
 */
public record ResumeManualRequest(
        @Size(max = 120, message = "标题最多 120 字") String title,
        @Size(min = 20, max = 50000, message = "简历内容长度需为 20-50000") String rawText,
        Map<String, Object> structured,
        @Size(max = 500, message = "摘要最多 500 字") String summary,
        Boolean isDefault) {}
