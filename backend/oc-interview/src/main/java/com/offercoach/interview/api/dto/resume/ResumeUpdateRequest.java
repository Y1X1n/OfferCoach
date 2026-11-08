package com.offercoach.interview.api.dto.resume;

import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * 简历修正请求；三个字段均可选，structured 变更时重建摘要。
 */
public record ResumeUpdateRequest(
        @Size(max = 120, message = "标题最多 120 字") String title, Map<String, Object> structured, Boolean isDefault) {}
