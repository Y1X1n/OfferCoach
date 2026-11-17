package com.offercoach.pay.service.error;

import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 支付服务统一异常处理：对外错误体为 {@code {code, message, timestamp}}。
 */
@RestControllerAdvice
public class PayExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(PayExceptionHandler.class);

    @ExceptionHandler(PayException.class)
    public ResponseEntity<Map<String, Object>> handlePay(PayException exception) {
        PayErrorKind kind = exception.kind();
        if (kind.httpStatus() >= 500) {
            log.warn("支付服务异常：kind={} message={}", kind.name(), exception.getMessage(), exception);
        }
        return ResponseEntity.status(kind.httpStatus()).body(body(kind.code(), exception.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception exception) {
        log.error("支付服务未预期异常", exception);
        return ResponseEntity.status(PayErrorKind.INTERNAL_ERROR.httpStatus())
                .body(body(PayErrorKind.INTERNAL_ERROR.code(), PayErrorKind.INTERNAL_ERROR.message()));
    }

    private static Map<String, Object> body(String code, String message) {
        return Map.of(
                "code", code, "message", message, "timestamp", Instant.now().toString());
    }
}
