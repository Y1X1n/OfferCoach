package com.offercoach.interview.resume.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.offercoach.identity.application.quota.QuotaService;
import com.offercoach.identity.domain.quota.QuotaType;
import com.offercoach.interview.resume.application.port.ResumeRepository;
import com.offercoach.interview.resume.domain.Resume;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * 解析失败必须把额度退回去——包括"异步任务根本没跑起来、由看门狗兜底"这条路径。
 */
class ResumeParseFinalizerTest {

    private static final long RESUME_ID = 42L;
    private static final long USER_ID = 7L;

    private final ResumeRepository resumeRepository = mock(ResumeRepository.class);
    private final QuotaService quotaService = mock(QuotaService.class);
    private final ResumeParseFinalizer finalizer = new ResumeParseFinalizer(resumeRepository, quotaService);

    private static Resume resume(String structuredJson) {
        return new Resume(
                RESUME_ID,
                USER_ID,
                "简历",
                "FILE",
                "resumes/7/x.pdf",
                "x.pdf",
                "application/pdf",
                10L,
                "sha",
                null,
                "PARSING",
                null,
                "raw",
                structuredJson,
                null,
                null,
                false,
                null);
    }

    @Test
    void refundsWhenFirstParseFails() {
        when(resumeRepository.failIfParsing(eq(RESUME_ID), anyString())).thenReturn(true);
        when(resumeRepository.findAny(RESUME_ID)).thenReturn(Optional.of(resume(null)));

        assertThat(finalizer.markFailedAndRefund(RESUME_ID, "解析超时")).isTrue();

        verify(quotaService)
                .refund(
                        eq(USER_ID),
                        eq(QuotaType.RESUME),
                        eq(1),
                        eq("PARSE_REFUND"),
                        eq("RESUME"),
                        eq(RESUME_ID),
                        eq("resume-parse-refund:" + RESUME_ID));
    }

    /**
     * 重解析不额外扣费，失败退款等于凭空发额度。
     */
    @Test
    void doesNotRefundWhenResumeWasAlreadyParsed() {
        when(resumeRepository.failIfParsing(eq(RESUME_ID), anyString())).thenReturn(true);
        when(resumeRepository.findAny(RESUME_ID)).thenReturn(Optional.of(resume("{\"skills\":[]}")));

        assertThat(finalizer.markFailedAndRefund(RESUME_ID, "解析超时")).isTrue();

        verify(quotaService, never()).refund(anyLong(), any(), anyInt(), any(), any(), anyLong(), any());
    }

    /**
     * 并发完成（已 READY）时 failIfParsing 返回 0 行：不能再改状态、更不能退款。
     */
    @Test
    void skipsBothWhenNoLongerParsing() {
        when(resumeRepository.failIfParsing(eq(RESUME_ID), anyString())).thenReturn(false);

        assertThat(finalizer.markFailedAndRefund(RESUME_ID, "解析超时")).isFalse();

        verify(quotaService, never()).refund(anyLong(), any(), anyInt(), any(), any(), anyLong(), any());
        verify(resumeRepository, never()).findAny(anyLong());
    }

    /**
     * 退款失败必须冒泡，让外层事务整体回滚——否则就成了"状态已 FAILED、额度没退"的原始缺陷。
     */
    @Test
    void propagatesRefundFailureSoTransactionRollsBack() {
        when(resumeRepository.failIfParsing(eq(RESUME_ID), anyString())).thenReturn(true);
        when(resumeRepository.findAny(RESUME_ID)).thenReturn(Optional.of(resume(null)));
        when(quotaService.refund(anyLong(), any(), anyInt(), any(), any(), anyLong(), any()))
                .thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> finalizer.markFailedAndRefund(RESUME_ID, "解析超时"))
                .isInstanceOf(IllegalStateException.class);
    }
}
