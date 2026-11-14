package com.offercoach.ai.tools.error;

/**
 * 工具服务内部错误分类；映射为统一的 {@code {code, message, retryable}} 响应体。
 */
public enum ToolErrorKind {
    UNAUTHORIZED(false, "服务间鉴权失败"),
    INVALID_INPUT(false, "工具入参不合法"),
    TOO_LARGE(false, "文件超过大小限制"),
    UNSUPPORTED_FORMAT(false, "文件格式不支持"),
    CONTENT_REJECTED(false, "内容未通过审核"),
    INSUFFICIENT_BALANCE(false, "上游账户余额不足"),
    /** Fetch 被目标站点安全策略拒绝（上游 403）。 */
    BLOCKED(false, "目标站点拒绝抓取"),
    /** Fetch 未能获取到可提取的网页正文（上游 404）。 */
    NO_CONTENT(false, "未获取到可用网页内容"),
    UPSTREAM_BUSY(true, "上游服务繁忙，请稍后重试"),
    UPSTREAM_TIMEOUT(true, "上游服务超时"),
    UPSTREAM_FAILED(true, "上游服务调用失败"),
    NOT_CONFIGURED(false, "AI 未启用或未配置密钥"),
    BUSY(true, "服务并发已达上限，请稍后重试"),
    INTERNAL(false, "工具服务内部错误");

    private final boolean retryable;
    private final String message;

    ToolErrorKind(boolean retryable, String message) {
        this.retryable = retryable;
        this.message = message;
    }

    public boolean retryable() {
        return retryable;
    }

    public String message() {
        return message;
    }
}
