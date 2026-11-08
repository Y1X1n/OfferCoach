package com.offercoach.common.error;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

public record ApiError(
        String code,
        String message,
        String requestId,
        String traceId,
        Map<String, Object> details,
        OffsetDateTime timestamp) {

    public static ApiError of(
            ErrorCode errorCode, String message, Map<String, Object> details, String requestId, String traceId) {
        return new ApiError(
                errorCode.code(),
                message == null ? errorCode.message() : message,
                requestId,
                traceId,
                details == null ? Map.of() : Map.copyOf(details),
                OffsetDateTime.now(ZoneOffset.UTC));
    }
}
