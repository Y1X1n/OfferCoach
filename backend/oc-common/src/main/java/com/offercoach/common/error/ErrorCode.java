package com.offercoach.common.error;

/**
 * 对外错误码；与 docs/design/api.md §1.6 一一对应。
 */
public enum ErrorCode {
    VALIDATION_FAILED(400, "VALIDATION_FAILED", "参数校验失败"),
    UNAUTHENTICATED(401, "UNAUTHENTICATED", "请先登录"),
    TOKEN_EXPIRED(401, "TOKEN_EXPIRED", "登录状态已过期"),
    REFRESH_INVALID(401, "REFRESH_INVALID", "登录状态已失效，请重新登录"),
    QUOTA_EXCEEDED(402, "QUOTA_EXCEEDED", "额度不足，请升级套餐或购买次数包"),
    FORBIDDEN(403, "FORBIDDEN", "无权执行该操作"),
    RESOURCE_NOT_FOUND(404, "RESOURCE_NOT_FOUND", "资源不存在或无权查看"),
    STATE_CONFLICT(409, "STATE_CONFLICT", "当前状态不允许该操作"),
    DUPLICATE_ANSWER(409, "DUPLICATE_ANSWER", "该题已提交"),
    DUPLICATE_REQUEST(409, "DUPLICATE_REQUEST", "请勿重复提交"),
    FILE_TOO_LARGE(413, "FILE_TOO_LARGE", "文件超过大小限制"),
    UNSUPPORTED_FILE(415, "UNSUPPORTED_FILE", "文件类型不支持"),
    FILE_SCAN_FAILED(422, "FILE_SCAN_FAILED", "文件安全扫描未通过"),
    PARSE_FAILED(422, "PARSE_FAILED", "解析失败"),
    PARSE_NEED_MANUAL(422, "PARSE_NEED_MANUAL", "无法自动解析，请手动录入"),
    CONTENT_REJECTED(422, "CONTENT_REJECTED", "内容不合规，请修改后重试"),
    SESSION_LOCKED(423, "SESSION_LOCKED", "会话正被其他操作占用，请稍后重试"),
    RATE_LIMITED(429, "RATE_LIMITED", "请求过于频繁，请稍后重试"),
    STREAM_LIMIT_EXCEEDED(429, "STREAM_LIMIT_EXCEEDED", "并发流超限，请关闭旧流后重试"),
    INTERNAL_ERROR(500, "INTERNAL_ERROR", "服务暂时不可用，请稍后重试"),
    AI_OUTPUT_INVALID(502, "AI_OUTPUT_INVALID", "模型输出不合法"),
    QUESTION_GEN_FAILED(502, "QUESTION_GEN_FAILED", "出题失败，请重新创建"),
    ASR_FAILED(502, "ASR_FAILED", "语音转写失败"),
    AI_UNAVAILABLE(503, "AI_UNAVAILABLE", "AI 服务暂不可用"),
    AI_TIMEOUT(504, "AI_TIMEOUT", "AI 服务超时");

    private final int httpStatus;
    private final String code;
    private final String message;

    ErrorCode(int httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }

    public String format(Object... args) {
        return args.length == 0 ? message : String.format(message, args);
    }
}
