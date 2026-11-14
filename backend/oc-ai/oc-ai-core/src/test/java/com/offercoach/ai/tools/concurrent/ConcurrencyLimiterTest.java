package com.offercoach.ai.tools.concurrent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.offercoach.ai.tools.error.ToolErrorKind;
import com.offercoach.ai.tools.error.ToolException;
import org.junit.jupiter.api.Test;

/**
 * 在途限流：超出上限立即失败，释放后可再次获取。
 */
class ConcurrencyLimiterTest {

    @Test
    void rejectsWhenExhausted() {
        ConcurrencyLimiter limiter = new ConcurrencyLimiter("asr", 1);
        ConcurrencyLimiter.Permit permit = limiter.acquire();

        assertThatThrownBy(limiter::acquire)
                .isInstanceOf(ToolException.class)
                .extracting(ex -> ((ToolException) ex).kind())
                .isEqualTo(ToolErrorKind.BUSY);

        permit.close();
        ConcurrencyLimiter.Permit again = limiter.acquire();
        assertThat(again).isNotNull();
        again.close();
    }

    @Test
    void allowsConfiguredConcurrency() {
        ConcurrencyLimiter limiter = new ConcurrencyLimiter("ocr", 3);
        ConcurrencyLimiter.Permit first = limiter.acquire();
        ConcurrencyLimiter.Permit second = limiter.acquire();
        ConcurrencyLimiter.Permit third = limiter.acquire();

        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
        assertThat(third).isNotNull();

        first.close();
        second.close();
        third.close();
    }
}
