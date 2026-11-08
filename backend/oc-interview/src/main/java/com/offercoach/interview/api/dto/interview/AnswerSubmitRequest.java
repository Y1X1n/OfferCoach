package com.offercoach.interview.api.dto.interview;

import jakarta.validation.constraints.NotBlank;

/**
 * 提交文字回答；长度由服务层按配置校验。
 */
public record AnswerSubmitRequest(
        @NotBlank(message = "questionId 不能为空") String questionId,
        @NotBlank(message = "回答不能为空") String answerText,
        Integer durationMs,
        String inputMode) {}
