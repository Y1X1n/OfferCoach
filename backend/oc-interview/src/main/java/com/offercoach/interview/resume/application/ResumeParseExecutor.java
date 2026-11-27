package com.offercoach.interview.resume.application;

import com.offercoach.identity.application.port.UserRepository;
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
    /** 低于此长度视为疑似扫描件，先走 OCR 再结构化（见 ai-tools-prd.md §3.3）。 */
    private static final int OCR_TEXT_THRESHOLD = 200;

    private static final String ERROR_NEED_MANUAL = "PARSE_NEED_MANUAL";
    private static final String ERROR_PARSE_FAILED = "PARSE_FAILED";

    private final ResumeRepository resumeRepository;
    private final FileStorage fileStorage;
    private final DocumentTextExtractor documentTextExtractor;
    private final ResumeStructurer resumeStructurer;
    private final ResumeParseFinalizer finalizer;
    private final ResumeOcrFallback ocrFallback;
    private final UserRepository userRepository;

    public ResumeParseExecutor(
            ResumeRepository resumeRepository,
            FileStorage fileStorage,
            DocumentTextExtractor documentTextExtractor,
            ResumeStructurer resumeStructurer,
            ResumeParseFinalizer finalizer,
            ResumeOcrFallback ocrFallback,
            UserRepository userRepository) {
        this.resumeRepository = resumeRepository;
        this.fileStorage = fileStorage;
        this.documentTextExtractor = documentTextExtractor;
        this.resumeStructurer = resumeStructurer;
        this.finalizer = finalizer;
        this.ocrFallback = ocrFallback;
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
            String extension = extensionOf(resume);
            String text = "";
            int pageCount = 1;
            boolean localExtractionFailed = false;
            try {
                ExtractedDocument extracted = extract(resume);
                text = extracted.text() == null ? "" : extracted.text().strip();
                pageCount = Math.max(1, extracted.pageCount());
            } catch (DocumentExtractionException exception) {
                if (!ocrFallback.requiresOcr(extension)) {
                    throw exception;
                }
                // 图片 / PPT 无法本地抽取，直接走 OCR。
                localExtractionFailed = true;
            }
            if (localExtractionFailed || text.length() < OCR_TEXT_THRESHOLD || ocrFallback.requiresOcr(extension)) {
                String ocrText = ocrExtract(resume, text);
                if (ocrText != null && !ocrText.isEmpty()) {
                    text = ocrText;
                    pageCount = Math.max(pageCount, 1);
                }
            }
            if (text.length() < MIN_TEXT_CHARS) {
                log.warn("简历文本过短，转手动录入：resumeId={} length={}", resumeId, text.length());
                fail(resume, ERROR_NEED_MANUAL);
                return;
            }
            ResumeStructurer.StructuredResume structured = resumeStructurer.structure(text, allowRawToLlm(resume));
            finalizer.markReady(resumeId, text, structured, pageCount);
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

    /**
     * OCR 降级：图片 / PPT 或抽取文本过短时识别；失败返回 null 由调用方决定是否手动录入。
     */
    private String ocrExtract(Resume resume, String localText) {
        if (resume.fileKey() == null || resume.fileKey().isBlank()) {
            return null;
        }
        try {
            byte[] content = fileStorage.load(resume.fileKey());
            String text = ocrFallback.extract(resume.userId(), content, resume.fileName(), resume.mimeType());
            return text == null || text.isBlank() ? null : text;
        } catch (RuntimeException exception) {
            log.warn(
                    "简历 OCR 降级异常，保留本地文本：resumeId={} localLength={} reason={}",
                    resume.id(),
                    localText == null ? 0 : localText.length(),
                    exception.getClass().getSimpleName());
            return null;
        }
    }

    private void fail(Resume resume, String parseError) {
        // 状态迁移与退款同事务；失败时整体回滚，由看门狗在下一轮重试而不是把额度吞掉。
        try {
            finalizer.markFailedAndRefund(resume.id(), parseError);
        } catch (RuntimeException exception) {
            log.warn(
                    "简历解析失败落库与退款未完成，等待看门狗重试：resumeId={} reason={}",
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
