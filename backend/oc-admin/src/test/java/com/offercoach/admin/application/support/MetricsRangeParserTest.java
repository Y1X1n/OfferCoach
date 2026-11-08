package com.offercoach.admin.application.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.offercoach.common.error.BizException;
import com.offercoach.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

/**
 * 指标窗口只接受 1d/7d/30d。
 */
class MetricsRangeParserTest {

    @Test
    void parsesSupportedRanges() {
        assertEquals(1, MetricsRangeParser.days("1d"));
        assertEquals(7, MetricsRangeParser.days("7d"));
        assertEquals(30, MetricsRangeParser.days("30d"));
    }

    @Test
    void trimsAndIgnoresCase() {
        assertEquals(7, MetricsRangeParser.days(" 7D "));
    }

    @Test
    void rejectsUnsupportedRange() {
        BizException exception = assertThrows(BizException.class, () -> MetricsRangeParser.days("14d"));
        assertEquals(ErrorCode.VALIDATION_FAILED, exception.errorCode());
    }

    @Test
    void rejectsBlankRange() {
        assertThrows(BizException.class, () -> MetricsRangeParser.days(" "));
        assertThrows(BizException.class, () -> MetricsRangeParser.days(null));
    }
}
