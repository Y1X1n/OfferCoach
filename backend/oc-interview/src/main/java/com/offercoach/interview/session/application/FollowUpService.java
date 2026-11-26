package com.offercoach.interview.session.application;

import com.offercoach.ai.AiProvider;
import com.offercoach.ai.AiScene;
import com.offercoach.common.error.BizException;
import com.offercoach.common.error.ErrorCode;
import com.offercoach.interview.ai.AiCallLogger;
import com.offercoach.interview.ai.AiGateway;
import com.offercoach.interview.session.application.port.InterviewAnswerRepository;
import com.offercoach.interview.session.application.port.InterviewQuestionRepository;
import com.offercoach.interview.session.application.port.InterviewSessionRepository;
import com.offercoach.interview.session.domain.InterviewAnswer;
import com.offercoach.interview.session.domain.InterviewQuestion;
import com.offercoach.interview.session.domain.InterviewSession;
import com.offercoach.interview.session.domain.SessionStatus;
import com.offercoach.interview.support.JsonCodec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 单题追问：基于已提交回答生成 1 道追问，每场最多 2 道，计入题量但不额外扣额度。
 *
 * <p>AI 不可用或输出非法时退化为确定性追问，保证主流程可继续。</p>
 */
@Service
public class FollowUpService {

    private static final Logger log = LoggerFactory.getLogger(FollowUpService.class);
    private static final int MAX_FOLLOW_UPS = 2;
    private static final int MAX_ANSWER_CHARS = 1800;
    private static final String SYSTEM_PROMPT =
            """
            你是资深技术面试官。根据候选人上一题的回答，生成 1 道追问，继续深挖具体细节。
            只输出一个 JSON 对象，不要输出解释或 Markdown。JSON 结构：
            {"text":string,"referencePoints":[string],"whyThisQuestion":string}
            referencePoints 为 3-4 个评分要点；追问必须具体、可回答，不能重复原题或编造简历信息。
            """;
    private static final String FALLBACK_TEXT = "针对你刚才的回答，请补充一个更具体的例子：当时的背景、你采取的动作和最终结果分别是什么？";
    private static final List<String> FALLBACK_POINTS = List.of("背景与目标是否清楚", "个人动作是否具体", "结果是否有量化证据", "是否说明取舍与反思");

    private final InterviewSessionRepository sessionRepository;
    private final InterviewQuestionRepository questionRepository;
    private final InterviewAnswerRepository answerRepository;
    private final AiGateway aiGateway;
    private final AiCallLogger aiCallLogger;
    private final JsonCodec jsonCodec;
    private final TransactionTemplate transactionTemplate;

    public FollowUpService(
            InterviewSessionRepository sessionRepository,
            InterviewQuestionRepository questionRepository,
            InterviewAnswerRepository answerRepository,
            AiGateway aiGateway,
            AiCallLogger aiCallLogger,
            JsonCodec jsonCodec,
            TransactionTemplate transactionTemplate) {
        this.sessionRepository = sessionRepository;
        this.questionRepository = questionRepository;
        this.answerRepository = answerRepository;
        this.aiGateway = aiGateway;
        this.aiCallLogger = aiCallLogger;
        this.jsonCodec = jsonCodec;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * 生成并落库追问。
     *
     * <p>模型调用（最长 60s）刻意放在事务外：先做只读校验与生成，再在短事务里写入，
     * 避免长事务占用 Hikari 连接；写入前的上限与 seq 仍以事务内查询结果为准。</p>
     */
    public FollowUpQuestion add(long userId, long sessionId, long questionId) {
        InterviewSession session = sessionRepository
                .findOwned(userId, sessionId)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("sessionId", sessionId)));
        if (!SessionStatus.IN_PROGRESS.equals(session.status())) {
            throw new BizException(ErrorCode.STATE_CONFLICT, Map.of("status", session.status()));
        }
        InterviewQuestion parent = questionRepository
                .findOwned(sessionId, questionId)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("questionId", questionId)));
        InterviewAnswer answer = answerRepository
                .findByQuestionId(questionId)
                .orElseThrow(() -> new BizException(ErrorCode.STATE_CONFLICT, Map.of("reason", "请先提交回答")));
        if (answer.answerText() == null || answer.answerText().isBlank()) {
            throw new BizException(ErrorCode.STATE_CONFLICT, Map.of("reason", "回答内容为空"));
        }
        if (questionRepository.countFollowUps(sessionId) >= MAX_FOLLOW_UPS) {
            throw new BizException(ErrorCode.STATE_CONFLICT, Map.of("reason", "本场追问次数已用完"));
        }

        long startedAt = System.nanoTime();
        Draft draft = generate(parent, answer);
        boolean aiAvailable = aiGateway.available();
        FollowUpQuestion created = persist(sessionId, parent, draft);
        aiCallLogger.log(
                userId,
                "QUESTION_GEN",
                aiAvailable ? "openai-compatible" : "heuristic",
                aiAvailable ? "configured" : "rules-v1",
                draft.degraded() ? "DEGRADED" : "SUCCESS",
                0,
                0,
                null,
                (System.nanoTime() - startedAt) / 1_000_000,
                null,
                sessionId,
                null);
        log.info("追问已生成：sessionId={} parentQuestionId={} followUpId={}", sessionId, questionId, created.id());
        return created;
    }

    /**
     * 短事务：重新校验上限后写入追问并同步题量，把并发窗口压到最小。
     * 用 {@link TransactionTemplate} 而非同类内部调 {@code @Transactional}，避免自调用不走代理。
     */
    private FollowUpQuestion persist(long sessionId, InterviewQuestion parent, Draft draft) {
        return transactionTemplate.execute(status -> {
            if (questionRepository.countFollowUps(sessionId) >= MAX_FOLLOW_UPS) {
                throw new BizException(ErrorCode.STATE_CONFLICT, Map.of("reason", "本场追问次数已用完"));
            }
            int seq = questionRepository.maxSeq(sessionId) + 1;
            long id;
            try {
                id = questionRepository.insertFollowUp(
                        sessionId,
                        seq,
                        parent.dimension(),
                        parent.difficulty(),
                        draft.text(),
                        draft.referencePoints(),
                        draft.whyThisQuestion(),
                        parent.id());
            } catch (DataIntegrityViolationException exception) {
                throw new BizException(ErrorCode.STATE_CONFLICT, Map.of("reason", "FOLLOW_UP_CONFLICT"));
            }
            if (id == 0) {
                throw new BizException(ErrorCode.INTERNAL_ERROR);
            }
            if (!sessionRepository.incrementQuestionCount(sessionId)) {
                throw new BizException(ErrorCode.STATE_CONFLICT, Map.of("reason", "会话状态已变化"));
            }
            return new FollowUpQuestion(
                    id, seq, draft.text(), parent.dimension(), parent.difficulty(), draft.referencePoints());
        });
    }

    private Draft generate(InterviewQuestion parent, InterviewAnswer answer) {
        if (!aiGateway.available()) {
            return new Draft(FALLBACK_TEXT, FALLBACK_POINTS, "AI 不可用时的确定性追问", true);
        }
        try {
            String prompt = "原题：" + parent.text()
                    + "\n候选人回答：" + truncate(answer.answerText(), MAX_ANSWER_CHARS)
                    + "\n维度：" + parent.dimension()
                    + "\n难度：" + parent.difficulty();
            Optional<AiProvider.AiResult> result = aiGateway.complete(
                    AiScene.QUESTION_GEN, aiGateway.systemPrompt(AiScene.QUESTION_GEN, SYSTEM_PROMPT), prompt);
            if (result.isEmpty()) {
                return new Draft(FALLBACK_TEXT, FALLBACK_POINTS, "AI 不可用时的确定性追问", true);
            }
            Map<String, Object> parsed =
                    jsonCodec.readMap(jsonCodec.extractJsonObject(result.get().text()));
            String text = textOf(parsed.get("text"));
            if (text == null) {
                return new Draft(FALLBACK_TEXT, FALLBACK_POINTS, "AI 输出缺少题目文本", true);
            }
            List<String> points = pointsOf(parsed.get("referencePoints"));
            String why = textOf(parsed.get("whyThisQuestion"));
            return new Draft(
                    text, points.isEmpty() ? FALLBACK_POINTS : points, why == null ? "针对上一题回答继续深挖" : why, false);
        } catch (RuntimeException exception) {
            log.warn("追问 AI 输出非法，使用确定性追问：reason={}", exception.getClass().getSimpleName());
            return new Draft(FALLBACK_TEXT, FALLBACK_POINTS, "AI 输出非法时的确定性追问", true);
        }
    }

    private static String textOf(Object value) {
        if (value instanceof String text && !text.isBlank()) {
            return text.strip();
        }
        return null;
    }

    private static List<String> pointsOf(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> points = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof String text && !text.isBlank()) {
                points.add(text.strip());
            }
        }
        return points;
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private record Draft(String text, List<String> referencePoints, String whyThisQuestion, boolean degraded) {}

    public record FollowUpQuestion(
            long id, int seq, String text, String dimension, String difficulty, List<String> referencePoints) {}
}
