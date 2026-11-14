package com.offercoach.interview.session.domain;

import java.time.OffsetDateTime;

/**
 * 回答领域视图；answerText 仅在反馈评分与详情接口按归属读取，禁止写入日志。
 *
 * <p>语音作答时额外记录音频存储 key、时长与转写状态；文本作答时这些字段为空。</p>
 */
public record InterviewAnswer(
        long id,
        long sessionId,
        long questionId,
        long userId,
        String inputMode,
        String answerText,
        Integer durationMs,
        String status,
        OffsetDateTime submittedAt,
        String audioKey,
        String asrStatus) {}
