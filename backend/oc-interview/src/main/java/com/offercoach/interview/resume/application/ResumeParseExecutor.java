package com.offercoach.interview.resume.application;

import com.offercoach.identity.application.port.UserRepository;
import com.offercoach.identity.application.quota.QuotaService;
import com.offercoach.identity.domain.quota.QuotaType;
import com.offercoach.interview.document.DocumentExtractionException;
import com.offercoach.interview.document.DocumentTextExtractor;
import com.offercoach.interview.document.ExtractedDocument;
import com.offercoach.interview.resume.application.port.ResumeRepository;
import com.offercoach.interview.resume.domain.Resume;
import com.offercoach.interview.resume.domain.ResumeSource;
import com.offercoach.interview.storage.FileStorage;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 异步解析执行器：抽取文本、调用结构化、落库状态；失败退款但不记录原文与 PII。
 */
@Service
public class ResumeParseExecutor {

    private static final Logger log = LoggerFactory.getLogger(ResumeParseExecutor.class);

    private static final int MIN_TEXT_CHARS = 20;
    private static final String ERROR_NEED_MANUAL = "PARSE_NEED_MANUAL";
    private static final String ERROR_PARSE_FAILED = "PARSE_FAILED";

    private final ResumeRepository resumeRepository;
    private final FileStorage fileStorage;
    private final DocumentTextExtractor documentTextExtractor;
    private final ResumeStructurer resumeStructurer;
    private final ResumeParseFinalizer finalizer;
    private final QuotaService quotaService;
    private final UserRepository userRepository;

    public ResumeParseExecutor(
            ResumeRepository resumeRepository,
            FileStorage fileStorage,
            DocumentTextExtractor documentTextExtractor,
            ResumeStructurer resumeStructurer,
            ResumeParseFinalizer finalizer,
            QuotaService quotaService,
            UserRepository userRepository) {
        this.resumeRepository = resumeRepository;
        this.fileStorage = fileStorage;
        this.documentTextExtractor = documentTextExtractor;
        this.resumeStructurer = resumeStructurer;
        this.finalizer = finalizer;
        this.quotaService = quotaService;
        this.userRepository = userRepository;
    }

    public void execute(long resumeId) {
        Optional<Resume> found = resumeRepository.findAny(resumeId);
        if (found.isEmpty()) {
            return;
        }
        Resume resume = found.get();
        try {
            finalizer.markParsing(resumeId);
        } catch (RuntimeException exception) {
            log.warn(
                    "简历解析置 PARSING 失败：resumeId={} reason={}",
                    resumeId,
                    exception.getClass().getSimpleName());
            return;
        }

        try {
            ExtractedDocument extracted = extract(resume);
            String text = extracted.text() == null ? "" : extracted.text().strip();
            if (text.length() < MIN_TEXT_CHARS) {
                log.warn("简历文本过短，转手动录入：resumeId={} length={}", resumeId, text.length());
                fail(resume, ERROR_NEED_MANUAL);
                return;
            }
            ResumeStructurer.StructuredResume structured = resumeStructurer.structure(text, allowRawToLlm(resume));
            finalizer.markReady(resumeId, text, structured, Math.max(1, extracted.pageCount()));
        } catch (DocumentExtractionException exception) {
            log.warn("简历文本抽取失败：resumeId={} reason={}", resumeId, exception.getMessage());
            fail(resume, ERROR_PARSE_FAILED);
        } catch (RuntimeException exception) {
            log.warn(
                    "简历解析失败：resumeId={} reason={}",
                    resumeId,
                    exception.getClass().getSimpleName());
            fail(resume, ERROR_PARSE_FAILED);
        }
    }

    private boolean allowRawToLlm(Resume resume) {
        try {
            return userRepository.allowRawResumeToLlm(resume.userId());
        } catch (RuntimeException exception) {
            log.warn(
                    "读取简历原文授权失败，按不允许处理：resumeId={} reason={}",
                    resume.id(),
                    exception.getClass().getSimpleName());
            return false;
        }
    }

    private ExtractedDocument extract(Resume resume) {
        if (ResumeSource.MANUAL.equals(resume.sourceType())) {
            String rawText = resume.rawText() == null ? "" : resume.rawText();
            return new ExtractedDocument(rawText, 1);
        }
        if (resume.fileKey() == null || resume.fileKey().isBlank()) {
            throw new DocumentExtractionException("缺少文件 key");
        }
        byte[] content = fileStorage.load(resume.fileKey());
        ExtractedDocument extracted = documentTextExtractor.extract(content, extensionOf(resume));
        return new ExtractedDocument(DocumentTextExtractor.clean(extracted.text()), extracted.pageCount());
    }

    private void fail(Resume resume, String parseError) {
        try {
            finalizer.markFailed(resume.id(), parseError);
        } catch (RuntimeException exception) {
            log.warn(
                    "简历解析状态置 FAILED 失败：resumeId={} reason={}",
                    resume.id(),
                    exception.getClass().getSimpleName());
        }
        // 已成功解析过的简历重解析失败不退款；首次解析失败按幂等键退回当次扣减。
        if (resume.structuredJson() != null) {
            return;
        }
        try {
            quotaService.refund(
                    resume.userId(),
                    QuotaType.RESUME,
                    1,
                    "PARSE_REFUND",
                    "RESUME",
                    resume.id(),
                    "resume-parse-refund:" + resume.id());
        } catch (RuntimeException exception) {
            log.warn(
                    "简历解析失败退款未完成：resumeId={} reason={}",
                    resume.id(),
                    exception.getClass().getSimpleName());
        }
    }

    private static String extensionOf(Resume resume) {
        String fileName = resume.fileName();
        if (fileName != null) {
            int dot = fileName.lastIndexOf('.');
            if (dot >= 0 && dot < fileName.length() - 1) {
                return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
            }
        }
        if (resume.mimeType() != null
                && resume.mimeType().toLowerCase(Locale.ROOT).contains("pdf")) {
            return "pdf";
        }
        return "docx";
    }
}
