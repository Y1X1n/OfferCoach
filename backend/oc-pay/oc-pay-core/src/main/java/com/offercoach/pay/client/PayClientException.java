package com.offercoach.pay.client;

/**
 * 调用 oc-pay-service 失败时抛出；{@code status} 为上游 HTTP 状态（本地网络失败为 0）。
 */
public class PayClientException extends RuntimeException {

    private final int status;

    public PayClientException(String message, int status) {
        super(message);
        this.status = status;
    }

    public PayClientException(String message, int status, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public int status() {
        return status;
    }
}
