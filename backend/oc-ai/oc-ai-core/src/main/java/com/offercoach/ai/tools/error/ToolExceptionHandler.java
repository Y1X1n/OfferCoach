package com.offercoach.ai.tools.error;

import com.offercoach.ai.tools.api.ToolDtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 工具服务统一异常映射：把 {@link ToolException} 转为稳定错误体与状态码。
 */
@RestControllerAdvice
public class ToolExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ToolExceptionHandler.class);

    @ExceptionHandler(ToolException.class)
    public ResponseEntity<ToolDtos.ToolErrorBody> handle(ToolException exception) {
        if (exception.kind() == ToolErrorKind.INTERNAL) {
            log.warn("工具服务异常：kind={} message={}", exception.kind(), exception.getMessage());
        }
        HttpStatus status =
                switch (exception.kind()) {
                    case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
                    case INVALID_INPUT, UNSUPPORTED_FORMAT -> HttpStatus.BAD_REQUEST;
                    case TOO_LARGE -> HttpStatus.PAYLOAD_TOO_LARGE;
                    case BLOCKED -> HttpStatus.FORBIDDEN;
                    case NO_CONTENT -> HttpStatus.NOT_FOUND;
                    case CONTENT_REJECTED -> HttpStatus.UNPROCESSABLE_ENTITY;
                    case NOT_CONFIGURED, UPSTREAM_FAILED -> HttpStatus.BAD_GATEWAY;
                    case UPSTREAM_BUSY, BUSY -> HttpStatus.TOO_MANY_REQUESTS;
                    case UPSTREAM_TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
                    case INSUFFICIENT_BALANCE -> HttpStatus.PAYMENT_REQUIRED;
                    case INTERNAL -> HttpStatus.INTERNAL_SERVER_ERROR;
                };
        return ResponseEntity.status(status)
                .body(new ToolDtos.ToolErrorBody(
                        exception.kind().name(),
                        exception.getMessage(),
                        exception.kind().retryable()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ToolDtos.ToolErrorBody> handleIllegalArgument(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ToolDtos.ToolErrorBody(ToolErrorKind.INVALID_INPUT.name(), exception.getMessage(), false));
    }
}
