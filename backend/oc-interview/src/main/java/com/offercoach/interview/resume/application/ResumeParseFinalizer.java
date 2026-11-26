package com.offercoach.interview.resume.application;

import com.offercoach.interview.resume.application.port.ResumeRepository;
import com.offercoach.interview.resume.domain.ResumeStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 解析状态落库事务边界；短事务，避免把 IO / AI 调用纳入数据库事务。
 */
@Service
@RequiredArgsConstructor
public class ResumeParseFinalizer {

    private final ResumeRepository resumeRepository;

    @Transactional
    public void markParsing(long resumeId) {
        resumeRepository.updateParseState(resumeId, ResumeStatus.PARSING, null);
    }

    @Transactional
    public void markReady(long resumeId, String rawText, ResumeStructurer.StructuredResume structured, int pageCount) {
        resumeRepository.markReady(
                resumeId, rawText, structured.json(), structured.summary(), structured.summaryHash(), pageCount);
    }

    /**
     * 只在仍处于解析中时置 FAILED。
     *
     * <p>用带状态谓词的 {@code failIfParsing}：解析任务超时后迟到的 worker 若直接写 FAILED，
     * 会把已经 READY 的简历（用户已看到结构化结果）覆盖成失败态。</p>
     */
    @Transactional
    public void markFailed(long resumeId, String parseError) {
        resumeRepository.failIfParsing(resumeId, parseError);
    }
}
