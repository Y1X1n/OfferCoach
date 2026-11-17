package com.offercoach.pay.service.error;

/**
 * 支付业务异常；由 {@link PayExceptionHandler} 映射为对应 HTTP 状态与错误体。
 */
public class PayException extends RuntimeException {

    private final PayErrorKind kind;

    public PayException(PayErrorKind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public PayException(PayErrorKind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public PayErrorKind kind() {
        return kind;
    }
}
