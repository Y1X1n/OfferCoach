package com.offercoach.interview.feedback.infrastructure.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.offercoach.interview.feedback.application.port.AnswerFeedbackRepository;
import com.offercoach.interview.feedback.domain.AnswerFeedback;
import com.offercoach.interview.feedback.domain.FeedbackEvidence;
import com.offercoach.interview.support.JsonCodec;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 反馈仓储实现：JSONB 文本解析失败时按空集合处理，分数缺失按 0 处理。
 */
@Repository
@RequiredArgsConstructor
public class AnswerFeedbackRepositoryImpl implements AnswerFeedbackRepository {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    private static final TypeReference<List<FeedbackEvidence>> EVIDENCE_LIST = new TypeReference<>() {};

    private final AnswerFeedbackMapper feedbackMapper;
    private final JsonCodec jsonCodec;

    @Override
    public Optional<AnswerFeedback> findLatestByAnswer(long userId, long answerId) {
        return Optional.ofNullable(toFeedback(feedbackMapper.findLatestByAnswer(userId, answerId)));
    }

    @Override
    public Optional<AnswerFeedback> findByRequestId(String requestId) {
        return Optional.ofNullable(toFeedback(feedbackMapper.findByRequestId(requestId)));
    }

    @Override
    public int insertIfAbsent(AnswerFeedback feedback) {
        AnswerFeedbackDO row = new AnswerFeedbackDO();
        row.setAnswerId(feedback.answerId());
        row.setVersion(feedback.version());
        row.setRequestId(feedback.requestId());
        row.setSessionId(feedback.sessionId());
        row.setUserId(feedback.userId());
        row.setOverallScore(feedback.overallScore());
        row.setDimensionsJson(jsonCodec.write(feedback.dimensions()));
        row.setStrengthsJson(jsonCodec.write(feedback.strengths()));
        row.setImprovementsJson(jsonCodec.write(feedback.improvements()));
        row.setEvidenceJson(jsonCodec.write(feedback.evidence()));
        row.setRewrittenAnswer(feedback.rewrittenAnswer());
        row.setModel(feedback.model());
        row.setProvider(feedback.provider());
        row.setDegraded(feedback.degraded());
        return feedbackMapper.insertIfAbsent(row);
    }

    @Override
    public List<AnswerFeedback> listBySession(long sessionId) {
        return feedbackMapper.listBySession(sessionId).stream()
                .map(this::toFeedback)
                .toList();
    }

    private AnswerFeedback toFeedback(AnswerFeedbackDO row) {
        if (row == null) {
            return null;
        }
        return new AnswerFeedback(
                row.getId() == null ? 0 : row.getId(),
                row.getAnswerId() == null ? 0 : row.getAnswerId(),
                row.getSessionId() == null ? 0 : row.getSessionId(),
                row.getUserId() == null ? 0 : row.getUserId(),
                row.getVersion() == null ? 1 : row.getVersion(),
                row.getRequestId(),
                row.getOverallScore() == null ? BigDecimal.ZERO : row.getOverallScore(),
                parseDimensions(row.getDimensionsJson()),
                parseStrings(row.getStrengthsJson()),
                parseStrings(row.getImprovementsJson()),
                parseEvidence(row.getEvidenceJson()),
                row.getRewrittenAnswer(),
                row.getModel(),
                row.getProvider(),
                Boolean.TRUE.equals(row.getDegraded()),
                row.getCreatedAt());
    }

    private Map<String, BigDecimal> parseDimensions(String json) {
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        if (json == null || json.isBlank()) {
            return result;
        }
        try {
            Map<String, Object> parsed = jsonCodec.readMap(json);
            for (Map.Entry<String, Object> entry : parsed.entrySet()) {
                if (entry.getValue() instanceof Number number) {
                    result.put(entry.getKey(), BigDecimal.valueOf(number.doubleValue()));
                }
            }
        } catch (IllegalArgumentException exception) {
            return result;
        }
        return result;
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

    private List<FeedbackEvidence> parseEvidence(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<FeedbackEvidence> parsed = jsonCodec.read(json, EVIDENCE_LIST);
            return parsed == null ? List.of() : new ArrayList<>(parsed);
        } catch (IllegalArgumentException exception) {
            return List.of();
        }
    }
}
