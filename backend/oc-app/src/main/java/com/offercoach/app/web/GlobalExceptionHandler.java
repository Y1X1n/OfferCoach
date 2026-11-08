package com.offercoach.app.web;

import com.offercoach.common.error.ApiError;
import com.offercoach.common.error.BizException;
import com.offercoach.common.error.ErrorCode;
import com.offercoach.common.trace.TraceContext;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiError> handleBiz(BizException exception) {
        ErrorCode code = exception.errorCode();
        return ResponseEntity.status(code.httpStatus())
                .body(ApiError.of(
                        code,
                        exception.getMessage(),
                        exception.details(),
                        TraceContext.requestId(),
                        TraceContext.traceId()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, Object> fields = exception.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        error -> error.getDefaultMessage() == null ? "参数不合法" : error.getDefaultMessage(),
                        (first, second) -> first,
                        LinkedHashMap::new));
        return badRequest(Map.of("fields", fields));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraint(ConstraintViolationException exception) {
        Map<String, Object> fields = exception.getConstraintViolations().stream()
                .collect(Collectors.toMap(
                        violation -> violation.getPropertyPath().toString(),
                        violation -> violation.getMessage(),
                        (first, second) -> first,
                        LinkedHashMap::new));
        return badRequest(Map.of("fields", fields));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException exception) {
        return badRequest(Map.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException exception) {
        return ResponseEntity.status(ErrorCode.FORBIDDEN.httpStatus())
                .body(ApiError.of(
                        ErrorCode.FORBIDDEN, null, Map.of(), TraceContext.requestId(), TraceContext.traceId()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(NoResourceFoundException exception) {
        return ResponseEntity.status(ErrorCode.RESOURCE_NOT_FOUND.httpStatus())
                .body(ApiError.of(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        null,
                        Map.of(),
                        TraceContext.requestId(),
                        TraceContext.traceId()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnknown(Exception exception) {
        log.error("unhandled error", exception);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.httpStatus())
                .body(ApiError.of(
                        ErrorCode.INTERNAL_ERROR, null, Map.of(), TraceContext.requestId(), TraceContext.traceId()));
    }

    private ResponseEntity<ApiError> badRequest(Map<String, Object> details) {
        return ResponseEntity.badRequest()
                .body(ApiError.of(
                        ErrorCode.VALIDATION_FAILED, null, details, TraceContext.requestId(), TraceContext.traceId()));
    }
}
