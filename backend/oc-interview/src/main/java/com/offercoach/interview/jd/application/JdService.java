package com.offercoach.interview.jd.application;

import com.offercoach.common.error.BizException;
import com.offercoach.common.error.ErrorCode;
import com.offercoach.interview.jd.application.port.JobDescriptionRepository;
import com.offercoach.interview.jd.domain.JobDescription;
import com.offercoach.interview.jd.domain.MatchResult;
import com.offercoach.interview.resume.application.port.ResumeRepository;
import com.offercoach.interview.resume.domain.Resume;
import com.offercoach.interview.support.JsonCodec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * JD 用例编排：结构化创建、查询、软删与简历匹配。
 */
@Service
public class JdService {

    private static final Logger log = LoggerFactory.getLogger(JdService.class);

    private static final int MIN_RAW_TEXT = 50;
    private static final int MAX_RAW_TEXT = 8000;
    private static final int MAX_PAGE_SIZE = 100;

    private final JobDescriptionRepository jdRepository;
    private final ResumeRepository resumeRepository;
    private final JdStructurer jdStructurer;
    private final JdMatcher jdMatcher;
    private final JsonCodec jsonCodec;

    public JdService(
            JobDescriptionRepository jdRepository,
            ResumeRepository resumeRepository,
            JdStructurer jdStructurer,
            JdMatcher jdMatcher,
            JsonCodec jsonCodec) {
        this.jdRepository = jdRepository;
        this.resumeRepository = resumeRepository;
        this.jdStructurer = jdStructurer;
        this.jdMatcher = jdMatcher;
        this.jsonCodec = jsonCodec;
    }

    public JdView create(long userId, String title, String company, String rawText) {
        String text = rawText == null ? "" : rawText.strip();
        if (text.length() < MIN_RAW_TEXT || text.length() > MAX_RAW_TEXT) {
            throw new BizException(
                    ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of("rawText", "JD 原文长度需为 50-8000")));
        }
        JdStructurer.StructuredJd structured = jdStructurer.structure(title, company, text);
        String safeTitle = blankToNull(title);
        String safeCompany = blankToNull(company);
        long id = jdRepository.insert(new JobDescription(
                0, userId, safeTitle, safeCompany, text, structured.json(), null, null, sha256(text), null));
        return new JdView(
                id,
                safeTitle,
                safeCompany,
                text,
                structured.structured(),
                null,
                null,
                OffsetDateTime.now(ZoneOffset.UTC));
    }

    @Transactional(readOnly = true)
    public JdPage list(long userId, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        long total = jdRepository.count(userId);
        long offset = (long) (safePage - 1) * safeSize;
        List<JdSummaryView> items = offset >= total
                ? List.of()
                : jdRepository.list(userId, (int) offset, safeSize).stream()
                        .map(this::toSummaryView)
                        .toList();
        return new JdPage(items, total, safePage, safeSize);
    }

    @Transactional(readOnly = true)
    public JdView detail(long userId, long jdId) {
        return toView(loadOwned(userId, jdId));
    }

    @Transactional
    public void delete(long userId, long jdId) {
        loadOwned(userId, jdId);
        jdRepository.markDeleted(userId, jdId);
    }

    /**
     * 简历匹配：校验双方归属后计算并持久化 match_score / match_report。
     */
    public MatchResult match(long userId, long jdId, long resumeId) {
        JobDescription jd = loadOwned(userId, jdId);
        Resume resume = resumeRepository
                .findOwned(userId, resumeId)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("resumeId", resumeId)));
        MatchResult result = jdMatcher.match(jd, resume);
        BigDecimal score = BigDecimal.valueOf(result.matchScore()).setScale(1, java.math.RoundingMode.HALF_UP);
        jdRepository.updateMatch(jdId, score, jsonCodec.write(result));
        log.info("JD 匹配完成：jdId={} resumeId={} degraded={}", jdId, resumeId, result.degraded());
        return result;
    }

    private JobDescription loadOwned(long userId, long jdId) {
        return jdRepository
                .findOwned(userId, jdId)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("jdId", jdId)));
    }

    private JdView toView(JobDescription jd) {
        return new JdView(
                jd.id(),
                jd.title(),
                jd.company(),
                jd.rawText(),
                parseJson(jd.structuredJson()),
                jd.matchScore(),
                parseJson(jd.matchReport()),
                jd.createdAt());
    }

    private Map<String, Object> parseJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return jsonCodec.readMap(json);
        } catch (IllegalArgumentException exception) {
            return Map.of();
        }
    }

    private JdSummaryView toSummaryView(JobDescription jd) {
        return new JdSummaryView(
                jd.id(), jd.title(), jd.company(), jd.matchScore(), parseJson(jd.structuredJson()), jd.createdAt());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    public record JdView(
            long id,
            String title,
            String company,
            String rawText,
            Map<String, Object> structured,
            BigDecimal matchScore,
            Map<String, Object> matchReport,
            OffsetDateTime createdAt) {}

    public record JdSummaryView(
            long id,
            String title,
            String company,
            BigDecimal matchScore,
            Map<String, Object> structured,
            OffsetDateTime createdAt) {}

    public record JdPage(List<JdSummaryView> items, long total, int page, int size) {}
}
