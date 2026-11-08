package com.offercoach.admin.api;

import com.offercoach.common.error.BizException;
import com.offercoach.common.error.ErrorCode;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 管理端 Controller 共用的 ID 解析与时间序列化。
 */
final class AdminWebSupport {

    private AdminWebSupport() {}

    static long parseId(String raw, String field) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException exception) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of(field, "非数字 ID")));
        }
    }

    static String timestamp(OffsetDateTime value) {
        return value == null ? null : value.toInstant().toString();
    }

    static String id(Long value) {
        return value == null ? null : Long.toString(value);
    }
}
