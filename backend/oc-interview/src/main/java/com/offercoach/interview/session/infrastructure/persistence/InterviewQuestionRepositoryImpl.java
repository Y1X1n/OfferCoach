package com.offercoach.interview.session.infrastructure.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.offercoach.interview.session.application.port.InterviewQuestionRepository;
import com.offercoach.interview.session.domain.InterviewQuestion;
import com.offercoach.interview.support.JsonCodec;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 题目仓储实现：referencePoints 从 JSONB 文本解析，解析失败按空列表处理。
 */
@Repository
@RequiredArgsConstructor
public class InterviewQuestionRepositoryImpl implements InterviewQuestionRepository {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private final InterviewQuestionMapper questionMapper;
    private final JsonCodec jsonCodec;

    @Override
    public long insert(InterviewQuestion question) {
        InterviewQuestionDO row = new InterviewQuestionDO();
        row.setSessionId(question.sessionId());
        row.setSeq(question.seq());
        row.setDimension(question.dimension());
        row.setDifficulty(question.difficulty());
        row.setQuestionText(question.text());
        row.setSource(question.source());
        row.setBankQuestionId(question.bankQuestionId());
        row.setReferencePointsJson(jsonCodec.write(question.referencePoints()));
        row.setContextJson(question.contextJson());
        row.setWhyThisQuestion(question.whyThisQuestion());
        row.setRetrievalJson(question.retrievalJson());
        row.setStatus(question.status());
        questionMapper.insert(row);
        return row.getId() == null ? 0 : row.getId();
    }

    @Override
    public List<InterviewQuestion> listBySession(long sessionId) {
        return questionMapper.listBySession(sessionId).stream()
                .map(this::toQuestion)
                .toList();
    }

    @Override
    public List<InterviewQuestion> listVisible(long sessionId, int currentSeq) {
        return questionMapper.listVisible(sessionId, currentSeq).stream()
                .map(this::toQuestion)
                .toList();
    }

    @Override
    public Optional<InterviewQuestion> findOwned(long sessionId, long questionId) {
        return Optional.ofNullable(toQuestion(questionMapper.findOwned(sessionId, questionId)));
    }

    @Override
    public long countBySession(long sessionId) {
        return questionMapper.countBySession(sessionId);
    }

    @Override
    public long countByStatus(long sessionId, String status) {
        return questionMapper.countByStatus(sessionId, status);
    }

    @Override
    public boolean markAsked(long sessionId, int seq) {
        return questionMapper.markAsked(sessionId, seq) > 0;
    }

    @Override
    public boolean markAnswered(long questionId) {
        return questionMapper.markAnswered(questionId) > 0;
    }

    @Override
    public boolean markSkipped(long questionId) {
        return questionMapper.markSkipped(questionId) > 0;
    }

    private InterviewQuestion toQuestion(InterviewQuestionDO row) {
        if (row == null) {
            return null;
        }
        return new InterviewQuestion(
                row.getId() == null ? 0 : row.getId(),
                row.getSessionId() == null ? 0 : row.getSessionId(),
                row.getSeq() == null ? 0 : row.getSeq(),
                row.getDimension(),
                row.getDifficulty(),
                row.getQuestionText(),
                row.getSource(),
                row.getBankQuestionId(),
                parseReferencePoints(row.getReferencePointsJson()),
                row.getWhyThisQuestion(),
                row.getRetrievalJson(),
                row.getContextJson(),
                row.getStatus());
    }

    private List<String> parseReferencePoints(String json) {
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
}
