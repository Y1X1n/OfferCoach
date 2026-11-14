package com.offercoach.ai.tools.error;

/**
 * 工具服务异常；携带分类与上游状态码，供调用方决定降级与重试。
 */
public class ToolException extends RuntimeException {

    private final ToolErrorKind kind;
    private final int upstreamStatus;

    public ToolException(ToolErrorKind kind) {
        this(kind, kind.message(), null, 0);
    }

    public ToolException(ToolErrorKind kind, String message) {
        this(kind, message, null, 0);
    }

    public ToolException(ToolErrorKind kind, String message, Throwable cause) {
        this(kind, message, cause, 0);
    }

    public ToolException(ToolErrorKind kind, String message, Throwable cause, int upstreamStatus) {
        super(message, cause);
        this.kind = kind;
        this.upstreamStatus = upstreamStatus;
    }

    public ToolErrorKind kind() {
        return kind;
    }

    public int upstreamStatus() {
        return upstreamStatus;
    }
}
