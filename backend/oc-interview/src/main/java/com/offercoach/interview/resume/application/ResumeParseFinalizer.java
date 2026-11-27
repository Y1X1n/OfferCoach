package com.offercoach.interview.resume.application;

import com.offercoach.identity.application.quota.QuotaService;
import com.offercoach.identity.domain.quota.QuotaType;
import com.offercoach.interview.resume.application.port.ResumeRepository;
import com.offercoach.interview.resume.domain.Resume;
import com.offercoach.interview.resume.domain.ResumeStatus;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 解析状态落库事务边界；短事务，避免把 IO / AI 调用纳入数据库事务。
 */
@Service
@RequiredArgsConstructor
public class ResumeParseFinalizer {

    private static final Logger log = LoggerFactory.getLogger(ResumeParseFinalizer.class);

    private static final String REFUND_REASON = "PARSE_REFUND";
    private static final String REFUND_REF_TYPE = "RESUME";

    private final ResumeRepository resumeRepository;
    private final QuotaService quotaService;

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

    /**
     * 置 FAILED 并退还本次解析额度；状态变更与退款在同一个事务里。
     *
     * <p>分两步提交时，若进程在两步之间退出（OOM、重启、连接抖动），简历已是 FAILED 而额度仍扣着，
     * 且没有任何任务会重试——看门狗只扫 PENDING/PARSING，已 FAILED 的行不会再被处理，
     * 用户就永久损失一次额度。同事务下要么都成功、要么都回滚，由下一轮看门狗再次触发。</p>
     *
     * <p>两条豁免：
     * <ul>
     *   <li>已成功解析过的简历（{@code structuredJson != null}）重解析失败不退款——重解析本身不额外扣费，
     *       退款等于凭空发额度；</li>
     *   <li>幂等键固定为 {@code resume-parse-refund:{id}}（按简历维度），执行器与看门狗重复触发只退一次。</li>
     * </ul>
     *
     * @return 是否真的完成了状态迁移；false 表示简历已不是解析中（并发完成或已被处理），调用方无需再处理
     */
    @Transactional
    public boolean markFailedAndRefund(long resumeId, String parseError) {
        if (!resumeRepository.failIfParsing(resumeId, parseError)) {
            return false;
        }
        Optional<Resume> resume = resumeRepository.findAny(resumeId);
        if (resume.isEmpty()
                || resume.get().structuredJson() != null
                || resume.get().userId() <= 0) {
            return true;
        }
        quotaService.refund(
                resume.get().userId(),
                QuotaType.RESUME,
                1,
                REFUND_REASON,
                REFUND_REF_TYPE,
                resumeId,
                "resume-parse-refund:" + resumeId);
        log.info("简历解析失败已退还额度：resumeId={} userId={}", resumeId, resume.get().userId());
        return true;
    }
}
