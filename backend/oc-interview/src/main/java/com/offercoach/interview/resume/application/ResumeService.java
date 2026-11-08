package com.offercoach.interview.resume.application;

import com.offercoach.common.error.BizException;
import com.offercoach.common.error.ErrorCode;
import com.offercoach.identity.application.quota.QuotaResult;
import com.offercoach.identity.application.quota.QuotaService;
import com.offercoach.identity.domain.quota.QuotaType;
import com.offercoach.interview.resume.application.port.ResumeRepository;
import com.offercoach.interview.resume.domain.Resume;
import com.offercoach.interview.resume.domain.ResumeSource;
import com.offercoach.interview.resume.domain.ResumeStatus;
import com.offercoach.interview.storage.FileStorage;
import com.offercoach.interview.support.JsonCodec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 简历用例编排：上传/手录、额度扣减、异步解析、查询、修正、软删与文件下载。
 */
@Service
public class ResumeService {

    private static final Logger log = LoggerFactory.getLogger(ResumeService.class);

    private static final long MAX_FILE_BYTES = 10L * 1024 * 1024;
    private static final long ESTIMATED_READY_MS = 5000L;
    private static final long DOWNLOAD_URL_EXPIRES_IN = 900L;
    private static final int MAX_TITLE_LENGTH = 120;
    private static final int MIN_MANUAL_TEXT = 20;
    private static final int MAX_MANUAL_TEXT = 50000;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int REPARSE_HOURLY_LIMIT = 2;
    private static final String REPARSE_KEY_PREFIX = "oc:resume:reparse:";
    private static final DateTimeFormatter HOUR_BUCKET = DateTimeFormatter.ofPattern("yyyyMMddHH");
    private static final String DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final Set<String> PDF_MIMES = Set.of("application/pdf", "application/x-pdf");

    private final ResumeRepository resumeRepository;
    private final FileStorage fileStorage;
    private final QuotaService quotaService;
    private final ApplicationEventPublisher eventPublisher;
    private final ResumeStructurer resumeStructurer;
    private final ResumeSummaryBuilder summaryBuilder;
    private final ResumeConfidenceInspector confidenceInspector;
    private final JsonCodec jsonCodec;
    private final StringRedisTemplate redisTemplate;

    public ResumeService(
            ResumeRepository resumeRepository,
            FileStorage fileStorage,
            QuotaService quotaService,
            ApplicationEventPublisher eventPublisher,
            ResumeStructurer resumeStructurer,
            ResumeSummaryBuilder summaryBuilder,
            ResumeConfidenceInspector confidenceInspector,
            JsonCodec jsonCodec,
            StringRedisTemplate redisTemplate) {
        this.resumeRepository = resumeRepository;
        this.fileStorage = fileStorage;
        this.quotaService = quotaService;
        this.eventPublisher = eventPublisher;
        this.resumeStructurer = resumeStructurer;
        this.summaryBuilder = summaryBuilder;
        this.confidenceInspector = confidenceInspector;
        this.jsonCodec = jsonCodec;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 上传文件建简历：先落库并扣额度，事务提交后异步解析；同用户同 SHA-256 重复返回 409。
     */
    @Transactional
    public ResumeCreated createFromFile(long userId, MultipartFile file, String title, boolean isDefault) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of("file", "文件为空")));
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new BizException(ErrorCode.FILE_TOO_LARGE, Map.of("maxBytes", MAX_FILE_BYTES));
        }
        String originalName = fileNameOf(file.getOriginalFilename());
        String extension = extensionOf(originalName);
        if (!"pdf".equals(extension) && !"docx".equals(extension)) {
            throw new BizException(ErrorCode.UNSUPPORTED_FILE, Map.of("extension", extension));
        }
        validateMime(extension, file.getContentType());

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException exception) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of("file", "文件读取失败")));
        }
        validateMagic(extension, content);
        String sha256 = sha256(content);
        resumeRepository.findBySha256(userId, sha256).ifPresent(existing -> {
            throw new BizException(ErrorCode.STATE_CONFLICT, Map.of("existingResumeId", existing.id()));
        });

        String safeTitle = normalizeTitle(title, originalName);
        String fileKey = "resumes/" + userId + "/" + sha256 + "." + extension;
        fileStorage.store(fileKey, content);
        if (isDefault) {
            resumeRepository.clearDefault(userId);
        }
        try {
            long resumeId = resumeRepository.insert(Resume.uploaded(
                    userId,
                    safeTitle,
                    fileKey,
                    originalName,
                    file.getContentType(),
                    content.length,
                    sha256,
                    isDefault));
            quotaService.reserveDailyResumeParse(userId);
            QuotaResult quota = quotaService.charge(
                    userId, QuotaType.RESUME, 1, "PARSE_CHARGE", "RESUME", resumeId, "resume-parse:" + resumeId);
            eventPublisher.publishEvent(new ResumeParseRequested(resumeId));
            return new ResumeCreated(
                    resumeId,
                    safeTitle,
                    ResumeStatus.PENDING,
                    ESTIMATED_READY_MS,
                    quota.charged() ? 1 : 0,
                    quota.balanceAfter());
        } catch (DuplicateKeyException exception) {
            throw new BizException(ErrorCode.STATE_CONFLICT);
        } catch (RuntimeException exception) {
            fileStorage.delete(fileKey);
            throw exception;
        }
    }

    /**
     * 手动结构化建档：前端编辑器直接提交结构化字段，不再走异步解析。
     * 额度仍按一次简历解析扣减，便于与上传路径统一核算。
     */
    @Transactional
    public ResumeCreated createManualStructured(
            long userId, String title, Map<String, Object> structured, String summary, boolean isDefault) {
        if (structured == null || structured.isEmpty() || !resumeStructurer.isValid(structured)) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of("structured", "结构不合法")));
        }
        resumeStructurer.normalize(structured);
        String structuredJson = jsonCodec.write(structured);
        String summaryText = summary == null || summary.isBlank() ? summaryBuilder.build(structured) : summary.strip();
        String sha256 = sha256(structuredJson.getBytes(StandardCharsets.UTF_8));
        resumeRepository.findBySha256(userId, sha256).ifPresent(existing -> {
            throw new BizException(ErrorCode.STATE_CONFLICT, Map.of("existingResumeId", existing.id()));
        });
        String safeTitle = normalizeTitle(title, "手动简历");
        if (isDefault) {
            resumeRepository.clearDefault(userId);
        }
        long resumeId;
        try {
            resumeId = resumeRepository.insert(Resume.manualStructured(
                    userId,
                    safeTitle,
                    structuredJson,
                    summaryText,
                    summaryBuilder.sha256(summaryText),
                    sha256,
                    isDefault));
        } catch (DuplicateKeyException exception) {
            throw new BizException(ErrorCode.STATE_CONFLICT);
        }
        quotaService.reserveDailyResumeParse(userId);
        QuotaResult quota = quotaService.charge(
                userId, QuotaType.RESUME, 1, "PARSE_CHARGE", "RESUME", resumeId, "resume-parse:" + resumeId);
        return new ResumeCreated(
                resumeId, safeTitle, ResumeStatus.READY, 0L, quota.charged() ? 1 : 0, quota.balanceAfter());
    }

    /**
     * 手动粘贴建简历：同样扣额度并异步解析。
     */
    @Transactional
    public ResumeCreated createManual(long userId, String title, String rawText, boolean isDefault) {
        String text = rawText == null ? "" : rawText.strip();
        if (text.length() < MIN_MANUAL_TEXT || text.length() > MAX_MANUAL_TEXT) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of("rawText", "内容长度需为 20-50000")));
        }
        String sha256 = sha256(text.getBytes(StandardCharsets.UTF_8));
        resumeRepository.findBySha256(userId, sha256).ifPresent(existing -> {
            throw new BizException(ErrorCode.STATE_CONFLICT, Map.of("existingResumeId", existing.id()));
        });

        String safeTitle = normalizeTitle(title, "手动简历");
        if (isDefault) {
            resumeRepository.clearDefault(userId);
        }
        long resumeId;
        try {
            resumeId =
                    resumeRepository.insert(Resume.manual(userId, safeTitle, text, sha256, text.length(), isDefault));
        } catch (DuplicateKeyException exception) {
            throw new BizException(ErrorCode.STATE_CONFLICT);
        }
        quotaService.reserveDailyResumeParse(userId);
        QuotaResult quota = quotaService.charge(
                userId, QuotaType.RESUME, 1, "PARSE_CHARGE", "RESUME", resumeId, "resume-parse:" + resumeId);
        eventPublisher.publishEvent(new ResumeParseRequested(resumeId));
        return new ResumeCreated(
                resumeId,
                safeTitle,
                ResumeStatus.PENDING,
                ESTIMATED_READY_MS,
                quota.charged() ? 1 : 0,
                quota.balanceAfter());
    }

    @Transactional(readOnly = true)
    public ResumePage list(long userId, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        long total = resumeRepository.count(userId);
        long offset = (long) (safePage - 1) * safeSize;
        List<ResumeSummaryView> items = offset >= total
                ? List.of()
                : resumeRepository.list(userId, (int) offset, safeSize).stream()
                        .map(ResumeService::toSummaryView)
                        .toList();
        return new ResumePage(items, total, safePage, safeSize);
    }

    @Transactional(readOnly = true)
    public ResumeView detail(long userId, long resumeId) {
        return toView(loadOwned(userId, resumeId));
    }

    /**
     * 修正标题 / 结构 / 默认标记；结构化变更时同步重建摘要。
     */
    @Transactional
    public ResumeView update(
            long userId, long resumeId, String title, Map<String, Object> structured, Boolean isDefault) {
        loadOwned(userId, resumeId);
        if (title != null) {
            String safeTitle = title.strip();
            if (safeTitle.isEmpty()) {
                throw new BizException(ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of("title", "标题不能为空")));
            }
            resumeRepository.updateTitle(userId, resumeId, truncate(safeTitle, MAX_TITLE_LENGTH));
        }
        if (structured != null) {
            if (!resumeStructurer.isValid(structured)) {
                throw new BizException(ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of("structured", "结构不合法")));
            }
            resumeStructurer.normalize(structured);
            String summary = summaryBuilder.build(structured);
            resumeRepository.updateStructured(
                    resumeId, jsonCodec.write(structured), summary, summaryBuilder.sha256(summary));
        }
        if (Boolean.TRUE.equals(isDefault)) {
            resumeRepository.clearDefault(userId);
            resumeRepository.setDefault(userId, resumeId);
        } else if (Boolean.FALSE.equals(isDefault)) {
            resumeRepository.updateDefault(userId, resumeId, false);
        }
        return toView(loadOwned(userId, resumeId));
    }

    /**
     * 软删；存在进行中会话引用时拒绝。
     */
    @Transactional
    public void delete(long userId, long resumeId) {
        loadOwned(userId, resumeId);
        long activeSessions = resumeRepository.countActiveSessions(userId, resumeId);
        if (activeSessions > 0) {
            throw new BizException(ErrorCode.STATE_CONFLICT, Map.of("activeSessions", activeSessions));
        }
        resumeRepository.markDeleted(userId, resumeId);
    }

    /**
     * 重新解析：每小时最多 2 次，不重复扣额度，Redis 故障时放行。
     */
    @Transactional
    public ResumeReparse reparse(long userId, long resumeId) {
        Resume resume = loadOwned(userId, resumeId);
        if (ResumeStatus.PENDING.equals(resume.parseStatus()) || ResumeStatus.PARSING.equals(resume.parseStatus())) {
            throw new BizException(ErrorCode.STATE_CONFLICT, Map.of("parseStatus", resume.parseStatus()));
        }
        if (!allowReparse(resumeId)) {
            throw new BizException(ErrorCode.RATE_LIMITED, Map.of("limit", REPARSE_HOURLY_LIMIT, "window", "1h"));
        }
        resumeRepository.updateParseState(resumeId, ResumeStatus.PENDING, null);
        eventPublisher.publishEvent(new ResumeParseRequested(resumeId));
        return new ResumeReparse(resumeId, ResumeStatus.PENDING, ESTIMATED_READY_MS);
    }

    public DownloadUrl downloadUrl(long userId, long resumeId) {
        Resume resume = loadOwned(userId, resumeId);
        if (!ResumeSource.FILE.equals(resume.sourceType())) {
            throw new BizException(ErrorCode.STATE_CONFLICT, Map.of("reason", "MANUAL_RESUME"));
        }
        return new DownloadUrl("/api/v1/resumes/" + resumeId + "/file", DOWNLOAD_URL_EXPIRES_IN);
    }

    public ResumeFile file(long userId, long resumeId) {
        Resume resume = loadOwned(userId, resumeId);
        if (!ResumeSource.FILE.equals(resume.sourceType()) || resume.fileKey() == null) {
            throw new BizException(ErrorCode.STATE_CONFLICT, Map.of("reason", "MANUAL_RESUME"));
        }
        byte[] content = fileStorage.load(resume.fileKey());
        String fileName = resume.fileName() == null ? "resume" : resume.fileName();
        String mimeType = resume.mimeType() == null ? "application/octet-stream" : resume.mimeType();
        return new ResumeFile(content, fileName, mimeType);
    }

    private Resume loadOwned(long userId, long resumeId) {
        return resumeRepository
                .findOwned(userId, resumeId)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("resumeId", resumeId)));
    }

    private ResumeView toView(Resume resume) {
        Map<String, Object> structured = parseStructured(resume.structuredJson());
        return new ResumeView(
                resume.id(),
                resume.title(),
                resume.parseStatus(),
                resume.sourceType(),
                resume.fileName(),
                resume.mimeType(),
                resume.fileSize(),
                resume.pageCount(),
                structured,
                resume.summary(),
                confidenceInspector.lowConfidenceFields(structured),
                resume.parseError(),
                resume.isDefault(),
                resume.createdAt());
    }

    private Map<String, Object> parseStructured(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return jsonCodec.readMap(json);
        } catch (IllegalArgumentException exception) {
            log.warn("简历结构化 JSON 无法解析，按空对象返回");
            return Map.of();
        }
    }

    private static ResumeSummaryView toSummaryView(Resume resume) {
        return new ResumeSummaryView(
                resume.id(),
                resume.title(),
                resume.parseStatus(),
                resume.sourceType(),
                resume.fileName(),
                resume.pageCount(),
                resume.summary(),
                resume.isDefault(),
                resume.parseError(),
                resume.createdAt());
    }

    private boolean allowReparse(long resumeId) {
        String key = REPARSE_KEY_PREFIX + resumeId + ":" + LocalDateTime.now().format(HOUR_BUCKET);
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, java.time.Duration.ofHours(2));
            }
            return count == null || count <= REPARSE_HOURLY_LIMIT;
        } catch (RuntimeException exception) {
            log.warn(
                    "重解析限流 Redis 不可用，放行：resumeId={} reason={}",
                    resumeId,
                    exception.getClass().getSimpleName());
            return true;
        }
    }

    private static void validateMagic(String extension, byte[] content) {
        boolean valid =
                switch (extension) {
                    case "pdf" -> content.length >= 5
                            && content[0] == '%'
                            && content[1] == 'P'
                            && content[2] == 'D'
                            && content[3] == 'F'
                            && content[4] == '-';
                    case "docx" -> content.length >= 4
                            && content[0] == 'P'
                            && content[1] == 'K'
                            && content[2] == 0x03
                            && content[3] == 0x04;
                    default -> false;
                };
        if (!valid) {
            throw new BizException(ErrorCode.UNSUPPORTED_FILE, Map.of("reason", "文件内容与扩展名不匹配"));
        }
    }

    private static void validateMime(String extension, String mimeType) {
        if (mimeType == null || mimeType.isBlank() || "application/octet-stream".equalsIgnoreCase(mimeType)) {
            return;
        }
        String normalized = mimeType.toLowerCase(Locale.ROOT);
        boolean compatible = "pdf".equals(extension) ? PDF_MIMES.contains(normalized) : DOCX_MIME.equals(normalized);
        if (!compatible) {
            throw new BizException(ErrorCode.UNSUPPORTED_FILE, Map.of("mimeType", mimeType));
        }
    }

    private static String fileNameOf(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            return null;
        }
        String cleaned = originalName.replace('\\', '/');
        int slash = cleaned.lastIndexOf('/');
        String name = slash >= 0 ? cleaned.substring(slash + 1) : cleaned;
        return name.isBlank() ? null : name;
    }

    private static String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot < 0 || dot == fileName.length() - 1
                ? ""
                : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String normalizeTitle(String title, String fallback) {
        String candidate = title == null || title.isBlank() ? fallback : title;
        if (candidate == null || candidate.isBlank()) {
            candidate = "未命名简历";
        }
        String cleaned = candidate.replaceAll("[\\r\\n\\t]", " ").strip();
        return truncate(cleaned, MAX_TITLE_LENGTH);
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private static String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    public record ResumeCreated(
            long id, String title, String parseStatus, long estimatedReadyInMs, int charged, int resumeCredits) {}

    public record ResumeSummaryView(
            long id,
            String title,
            String parseStatus,
            String sourceType,
            String fileName,
            Integer pageCount,
            String summary,
            boolean isDefault,
            String parseError,
            OffsetDateTime createdAt) {}

    public record ResumeView(
            long id,
            String title,
            String parseStatus,
            String sourceType,
            String fileName,
            String mimeType,
            Long fileSize,
            Integer pageCount,
            Map<String, Object> structured,
            String summary,
            List<String> lowConfidenceFields,
            String parseError,
            boolean isDefault,
            OffsetDateTime createdAt) {}

    public record ResumePage(List<ResumeSummaryView> items, long total, int page, int size) {}

    public record ResumeReparse(long id, String parseStatus, long estimatedReadyInMs) {}

    public record DownloadUrl(String url, long expiresIn) {}

    public record ResumeFile(byte[] content, String fileName, String mimeType) {}
}
