package com.offercoach.pay.service.error;

/**
 * 支付服务错误类型；与对外错误体 {@code {code, message}} 的 code 一致。
 */
public enum PayErrorKind {
    VALIDATION_FAILED(400, "VALIDATION_FAILED", "参数校验失败"),
    UNAUTHENTICATED(401, "UNAUTHENTICATED", "内部令牌无效"),
    NOT_FOUND(404, "RESOURCE_NOT_FOUND", "订单不存在"),
    STATE_CONFLICT(409, "STATE_CONFLICT", "当前状态不允许该操作"),
    NOT_CONFIGURED(503, "CHANNEL_NOT_CONFIGURED", "支付渠道未配置"),
    UPSTREAM_FAILED(502, "CHANNEL_UPSTREAM_FAILED", "支付渠道调用失败"),
    INTERNAL_ERROR(500, "INTERNAL_ERROR", "服务暂时不可用");

    private final int httpStatus;
    private final String code;
    private final String message;

    PayErrorKind(int httpStatus, String code, String message) {
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
}
