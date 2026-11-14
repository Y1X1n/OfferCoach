package com.offercoach.interview.api.dto.interview;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 创建会话请求；配置缺省值由服务层补齐。
 */
public record CreateSessionRequest(
        @NotBlank(message = "resumeId 不能为空") String resumeId,
        String jdId,
        String previewToken,
        @NotNull(message = "config 不能为空") @Valid Config config) {

    public record Config(
            Integer questionCount,
            String difficulty,
            String interviewerStyle,
            String answerMode,
            Integer timeLimitSec,
            String language) {}
}
