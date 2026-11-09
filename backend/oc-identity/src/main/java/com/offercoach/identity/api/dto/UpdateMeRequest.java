package com.offercoach.identity.api.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 更新当前用户资料；字段为 null 表示不修改。
 */
public record UpdateMeRequest(
        @Size(min = 2, max = 40, message = "昵称长度需为 2-40") String nickname,
        @Size(max = 64, message = "时区过长") String timezone,
        @Size(max = 1024, message = "头像地址过长") @Pattern(regexp = "^https://.*$", message = "头像地址必须使用 https")
                String avatarUrl,
        Boolean allowRawResumeToLlm) {}
