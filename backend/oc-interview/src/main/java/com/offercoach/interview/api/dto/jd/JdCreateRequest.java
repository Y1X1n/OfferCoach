package com.offercoach.interview.api.dto.jd;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建 JD 请求；rawText 50-8000 字符。
 */
public record JdCreateRequest(
        @Size(max = 160, message = "标题最多 160 字") String title,
        @Size(max = 160, message = "公司名最多 160 字") String company,
        @NotBlank(message = "JD 原文不能为空") @Size(min = 50, max = 8000, message = "JD 原文长度需为 50-8000") String rawText) {}
