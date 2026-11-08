package com.offercoach.admin.application.support;

import com.offercoach.common.error.BizException;
import com.offercoach.common.error.ErrorCode;
import java.util.Locale;
import java.util.Map;

/**
 * 指标时间窗口解析：只接受 1d / 7d / 30d。
 */
public final class MetricsRangeParser {

    private MetricsRangeParser() {}

    public static int days(String range) {
        String normalized = range == null ? null : range.strip().toLowerCase(Locale.ROOT);
        return switch (normalized == null ? "" : normalized) {
            case "1d" -> 1;
            case "7d" -> 7;
            case "30d" -> 30;
            default -> throw new BizException(
                    ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of("range", "仅支持 1d/7d/30d")));
        };
    }
}
