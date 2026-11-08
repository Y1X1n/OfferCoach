package com.offercoach.common.error;

import java.util.Map;

public class BizException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Map<String, Object> details;

    public BizException(ErrorCode errorCode) {
        this(errorCode, Map.of());
    }

    public BizException(ErrorCode errorCode, Map<String, Object> details, Object... args) {
        super(errorCode.format(args));
        this.errorCode = errorCode;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public Map<String, Object> details() {
        return details;
    }
}
