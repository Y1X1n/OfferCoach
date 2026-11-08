package com.offercoach.interview.api.dto.interview;

/**
 * 提交回答响应；stream 为短期评分流令牌。
 */
public record AnswerSubmitResponse(String answerId, String status, StreamInfo stream) {

    public record StreamInfo(String url, String answerId, String token, long expiresIn) {}
}
