package com.offercoach.ai;

/**
 * 供应商不可用或请求失败；调用方据此降级。
 */
public class AiUnavailableException extends RuntimeException {

    public AiUnavailableException(String message) {
        super(message);
    }

    public AiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
