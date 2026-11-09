package com.offercoach.interview.report.infrastructure.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.offercoach.interview.report.application.port.InterviewReportRepository;
import com.offercoach.interview.report.domain.InterviewReport;
import com.offercoach.interview.support.JsonCodec;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 报告仓储实现：结构字段从 JSONB 文本解析，解析失败按空集合返回。
 */
@Repository
@RequiredArgsConstructor
public class InterviewReportRepositoryImpl implements InterviewReportRepository {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    private static final TypeReference<List<InterviewReport.RadarItem>> RADAR_LIST = new TypeReference<>() {};
    private static final TypeReference<List<InterviewReport.ImprovementItem>> PLAN_LIST = new TypeReference<>() {};
    private static final TypeReference<List<InterviewReport.BreakdownItem>> BREAKDOWN_LIST = new TypeReference<>() {};

    private final InterviewReportMapper reportMapper;
    private final JsonCodec jsonCodec;

    @Override
    public long insertIfAbsent(long sessionId, long userId) {
        reportMapper.insertIfAbsent(sessionId, userId);
        InterviewReportDO row = reportMapper.findBySession(sessionId);
        return row == null || row.getId() == null ? 0 : row.getId();
    }

    @Override
    public Optional<InterviewReport> findOwned(long userId, long id) {
        return Optional.ofNullable(toReport(reportMapper.findOwned(userId, id)));
    }

    @Override
    public Optional<InterviewReport> findById(long id) {
        return Optional.ofNullable(toReport(reportMapper.findById(id)));
    }

    @Override
    public Optional<InterviewReport> findBySession(long sessionId) {
        return Optional.ofNullable(toReport(reportMapper.findBySession(sessionId)));
    }

    @Override
    public List<InterviewReport> list(long userId, int offset, int limit) {
        return reportMapper.list(userId, offset, limit).stream()
                .map(row -> toReport(row))
                .toList();
    }

    @Override
    public long count(long userId) {
        return reportMapper.count(userId);
    }

    @Override
    public List<Long> findStaleGenerating(int staleSeconds, int limit) {
        return reportMapper.findStaleGenerating(staleSeconds, limit);
    }

    @Override
    public boolean claim(long id, int staleSeconds, String generationToken) {
        return reportMapper.claim(id, staleSeconds, generationToken) > 0;
    }

    @Override
    public int markReady(
            long id,
            BigDecimal overallScore,
            String radarJson,
            String summary,
            String highlightsJson,
            String risksJson,
            String improvementPlanJson,
            String questionBreakdownJson,
            String generationToken) {
        return reportMapper.markReady(
                id,
                overallScore,
                radarJson,
                summary,
                highlightsJson,
                risksJson,
                improvementPlanJson,
                questionBreakdownJson,
                generationToken);
    }

    @Override
    public int incrementAttempts(long id, String lastError, String generationToken) {
        Integer attempts = reportMapper.incrementAttempts(id, lastError, generationToken);
        return attempts == null ? 0 : attempts;
    }

    @Override
    public int markFailed(long id, String lastError, String generationToken) {
        return reportMapper.markFailed(id, lastError, generationToken);
    }

    @Override
    public boolean resetForRetry(long userId, long id) {
        return reportMapper.resetForRetry(userId, id) > 0;
    }

    private InterviewReport toReport(InterviewReportDO row) {
        if (row == null) {
            return null;
        }
        return new InterviewReport(
                row.getId() == null ? 0 : row.getId(),
                row.getSessionId() == null ? 0 : row.getSessionId(),
                row.getUserId() == null ? 0 : row.getUserId(),
                row.getStatus(),
                row.getOverallScore(),
                parseRadar(row.getRadarJson()),
                row.getSummary(),
                parseStrings(row.getHighlightsJson()),
                parseStrings(row.getRisksJson()),
                parsePlan(row.getImprovementPlanJson()),
                parseBreakdown(row.getQuestionBreakdownJson()),
                row.getAttempts() == null ? 0 : row.getAttempts(),
                row.getLastError(),
                row.getGeneratedAt(),
                row.getCreatedAt());
    }

    private List<String> parseStrings(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> parsed = jsonCodec.read(json, STRING_LIST);
            return parsed == null ? List.of() : new ArrayList<>(parsed);
        } catch (IllegalArgumentException exception) {
            return List.of();
        }
    }

    private List<InterviewReport.RadarItem> parseRadar(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<InterviewReport.RadarItem> parsed = jsonCodec.read(json, RADAR_LIST);
            return parsed == null ? List.of() : new ArrayList<>(parsed);
        } catch (IllegalArgumentException exception) {
            return List.of();
        }
    }

    private List<InterviewReport.ImprovementItem> parsePlan(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<InterviewReport.ImprovementItem> parsed = jsonCodec.read(json, PLAN_LIST);
            return parsed == null ? List.of() : new ArrayList<>(parsed);
        } catch (IllegalArgumentException exception) {
            return List.of();
        }
    }

    private List<InterviewReport.BreakdownItem> parseBreakdown(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<InterviewReport.BreakdownItem> parsed = jsonCodec.read(json, BREAKDOWN_LIST);
            return parsed == null ? List.of() : new ArrayList<>(parsed);
        } catch (IllegalArgumentException exception) {
            return List.of();
        }
    }
}
