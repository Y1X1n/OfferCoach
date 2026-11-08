package com.offercoach.interview.session.application;

import com.offercoach.ai.AiProvider;
import com.offercoach.ai.AiScene;
import com.offercoach.interview.ai.AiCallLogger;
import com.offercoach.interview.ai.AiGateway;
import com.offercoach.interview.jd.application.port.JobDescriptionRepository;
import com.offercoach.interview.jd.domain.JobDescription;
import com.offercoach.interview.qbank.QuestionRetriever;
import com.offercoach.interview.qbank.QuestionRetriever.RetrievedQuestion;
import com.offercoach.interview.resume.application.port.ResumeRepository;
import com.offercoach.interview.resume.domain.Resume;
import com.offercoach.interview.session.application.QuestionContextBuilder.QuestionContext;
import com.offercoach.interview.session.application.port.InterviewQuestionRepository;
import com.offercoach.interview.session.application.port.InterviewSessionRepository;
import com.offercoach.interview.session.domain.InterviewQuestion;
import com.offercoach.interview.session.domain.InterviewSession;
import com.offercoach.interview.session.domain.QuestionPlan;
import com.offercoach.interview.session.domain.SessionConfig;
import com.offercoach.interview.session.domain.SessionStatus;
import com.offercoach.interview.support.JsonCodec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 出题 worker：题库召回优先（至少 60%），不足用 AI 生成，仍不足用模板兜底。
 * 逐题落库以便 SSE 展示进度；生成失败置 FAILED 并幂等退款。
 */
@Service
public class QuestionGenerationWorker {

    private static final Logger log = LoggerFactory.getLogger(QuestionGenerationWorker.class);

    private static final int MIN_QUESTION_COUNT = 1;
    private static final int MAX_CANDIDATES = 120;
    private static final int MAX_PROMPT_SUMMARY = 600;
    private static final String FAILURE_REASON = "QUESTION_GEN_FAILED";
    private static final String SOURCE_BANK = "BANK";
    private static final String SOURCE_GENERATED = "GENERATED";

    private static final String SYSTEM_PROMPT =
            """
            你是资深技术面试官。根据岗位要求与候选人简历摘要，生成结构化面试题。
            只输出一个 JSON 对象，不要输出解释或 Markdown。JSON 结构：
            {"questions":[{"dimension":"DEPTH|PROJECT|BEHAVIOR|FIT","difficulty":"EASY|MEDIUM|HARD",
            "text":string,"referencePoints":[string],"whyThisQuestion":string}]}
            题目必须具体、可回答，referencePoints 为 3-5 个评分要点，whyThisQuestion 说明与简历/JD 的关联。
            禁止编造简历中不存在的信息。
            """;

    private final InterviewSessionRepository sessionRepository;
    private final InterviewQuestionRepository questionRepository;
    private final ResumeRepository resumeRepository;
    private final JobDescriptionRepository jdRepository;
    private final QuestionRetriever questionRetriever;
    private final QuestionContextBuilder contextBuilder;
    private final InterviewSessionService sessionService;
    private final AiGateway aiGateway;
    private final AiCallLogger aiCallLogger;
    private final JsonCodec jsonCodec;

    public QuestionGenerationWorker(
            InterviewSessionRepository sessionRepository,
            InterviewQuestionRepository questionRepository,
            ResumeRepository resumeRepository,
            JobDescriptionRepository jdRepository,
            QuestionRetriever questionRetriever,
            QuestionContextBuilder contextBuilder,
            InterviewSessionService sessionService,
            AiGateway aiGateway,
            AiCallLogger aiCallLogger,
            JsonCodec jsonCodec) {
        this.sessionRepository = sessionRepository;
        this.questionRepository = questionRepository;
        this.resumeRepository = resumeRepository;
        this.jdRepository = jdRepository;
        this.questionRetriever = questionRetriever;
        this.contextBuilder = contextBuilder;
        this.sessionService = sessionService;
        this.aiGateway = aiGateway;
        this.aiCallLogger = aiCallLogger;
        this.jsonCodec = jsonCodec;
    }

    /**
     * 执行一次出题；非 PREPARING 或会话缺失时直接返回。
     */
    public void generate(long sessionId) {
        Optional<InterviewSession> found = sessionRepository.findAny(sessionId);
        if (found.isEmpty() || !SessionStatus.PREPARING.equals(found.get().status())) {
            return;
        }
        InterviewSession session = found.get();
        try {
            doGenerate(session);
        } catch (RuntimeException exception) {
            log.warn(
                    "出题失败：sessionId={} reason={}",
                    sessionId,
                    exception.getClass().getSimpleName());
            sessionService.markGenerationFailed(session.userId(), sessionId, FAILURE_REASON);
        }
    }

    private void doGenerate(InterviewSession session) {
        long startedAt = System.nanoTime();
        Optional<Resume> resume = resumeRepository.findOwned(session.userId(), session.resumeId());
        Optional<JobDescription> jd = jdRepository.findOwned(session.userId(), session.jdId());
        if (resume.isEmpty() || jd.isEmpty()) {
            throw new IllegalStateException("简历或 JD 已不可用");
        }
        QuestionContext context = contextBuilder.build(resume.get(), jd.get());
        SessionConfig config = sessionService.configOf(session);
        int count = Math.min(Math.max(config.questionCount(), MIN_QUESTION_COUNT), 20);
        QuestionPlan plan = QuestionPlan.of(count, config.difficulty());

        List<RetrievedQuestion> candidates = retrieve(context, count);
        List<QuestionSelection.Selected> selected = QuestionSelection.select(plan.slots(), candidates);
        List<PendingDraft> drafts = new ArrayList<>();
        for (QuestionSelection.Selected item : selected) {
            drafts.add(
                    item.fromBank()
                            ? PendingDraft.fromBank(item.slot(), item.question(), context)
                            : PendingDraft.missing(item.slot()));
        }
        int bankCount = (int) drafts.stream().filter(PendingDraft::fromBank).count();
        if (bankCount < plan.bankMinimum()) {
            log.info("题库候选不足目标配比：sessionId={} bank={} minimum={}", session.id(), bankCount, plan.bankMinimum());
        }
        if (drafts.stream().anyMatch(draft -> !draft.fromBank())) {
            fillWithAi(drafts, context);
        }
        persist(session.id(), drafts);
        sessionService.markReady(session.id());
        boolean aiAvailable = aiGateway.available();
        aiCallLogger.log(
                session.userId(),
                "QUESTION_GEN",
                aiAvailable ? "openai-compatible" : "heuristic",
                aiAvailable ? "configured" : "rules-v1",
                aiAvailable ? "SUCCESS" : "DEGRADED",
                0,
                0,
                null,
                (System.nanoTime() - startedAt) / 1_000_000,
                null);
        log.info(
                "出题完成：sessionId={} count={} bank={} generated={}",
                session.id(),
                drafts.size(),
                drafts.stream().filter(PendingDraft::fromBank).count(),
                drafts.stream().filter(draft -> !draft.fromBank()).count());
    }

    private List<RetrievedQuestion> retrieve(QuestionContext context, int count) {
        Set<String> keywords = context.keywords().isEmpty()
                ? Set.of()
                : Set.copyOf(context.keywords()
                        .subList(0, Math.min(context.keywords().size(), 12)));
        int limit = Math.min(MAX_CANDIDATES, Math.max(30, count * 6));
        try {
            return questionRetriever.retrieve(
                    new QuestionRetriever.RetrieveQuery(context.jobFamily(), keywords, limit));
        } catch (RuntimeException exception) {
            log.warn("题库召回失败，改走生成：reason={}", exception.getClass().getSimpleName());
            return List.of();
        }
    }

    private void fillWithAi(List<PendingDraft> drafts, QuestionContext context) {
        List<PendingDraft> missing =
                drafts.stream().filter(draft -> !draft.fromBank()).toList();
        if (missing.isEmpty()) {
            return;
        }
        List<AiDraft> aiDrafts = callAi(missing, context);
        int cursor = 0;
        for (PendingDraft draft : missing) {
            AiDraft chosen = null;
            for (int index = cursor; index < aiDrafts.size(); index++) {
                if (aiDrafts.get(index).dimension().equalsIgnoreCase(draft.slot.dimension())) {
                    chosen = aiDrafts.get(index);
                    aiDrafts.set(index, aiDrafts.get(cursor));
                    aiDrafts.set(cursor, chosen);
                    break;
                }
            }
            if (chosen == null && cursor < aiDrafts.size()) {
                chosen = aiDrafts.get(cursor);
            }
            if (chosen != null) {
                draft.fill(chosen);
            } else {
                draft.fill(TemplateQuestionFactory.create(draft.slot, missing.indexOf(draft) + 1, context));
            }
            cursor = Math.min(cursor + 1, aiDrafts.size());
        }
    }

    private List<AiDraft> callAi(List<PendingDraft> missing, QuestionContext context) {
        if (!aiGateway.available()) {
            return List.of();
        }
        try {
            StringBuilder slots = new StringBuilder();
            for (PendingDraft draft : missing) {
                slots.append("- ")
                        .append(draft.slot.dimension())
                        .append('/')
                        .append(draft.slot.difficulty())
                        .append('\n');
            }
            String prompt = "岗位：" + safe(context.jdTitle())
                    + "\n简历摘要：" + truncate(safe(context.resumeSummary()), MAX_PROMPT_SUMMARY)
                    + "\n技能：" + String.join("、", context.skills())
                    + "\nJD 关键词：" + String.join("、", context.keywords())
                    + "\n需要生成 " + missing.size() + " 道题，槽位：\n" + slots;
            Optional<AiProvider.AiResult> result = aiGateway.complete(
                    AiScene.QUESTION_GEN, aiGateway.systemPrompt(AiScene.QUESTION_GEN, SYSTEM_PROMPT), prompt);
            if (result.isEmpty()) {
                return List.of();
            }
            return parseAiDrafts(result.get().text());
        } catch (RuntimeException exception) {
            log.warn("AI 出题输出非法，改用模板：reason={}", exception.getClass().getSimpleName());
            return List.of();
        }
    }

    private List<AiDraft> parseAiDrafts(String text) {
        Map<String, Object> parsed = jsonCodec.readMap(jsonCodec.extractJsonObject(text));
        Object raw = parsed.get("questions");
        if (!(raw instanceof List<?> items)) {
            return List.of();
        }
        List<AiDraft> result = new ArrayList<>();
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            String dimension = textOrNull(map.get("dimension"));
            String difficulty = textOrNull(map.get("difficulty"));
            String questionText = textOrNull(map.get("text"));
            if (dimension == null || questionText == null) {
                continue;
            }
            List<String> points = new ArrayList<>();
            if (map.get("referencePoints") instanceof List<?> pointList) {
                for (Object point : pointList) {
                    String value = textOrNull(point);
                    if (value != null) {
                        points.add(value);
                    }
                }
            }
            result.add(new AiDraft(
                    dimension,
                    difficulty == null ? "MEDIUM" : difficulty,
                    questionText,
                    points.isEmpty() ? List.of("背景与目标", "关键行动", "量化结果") : points,
                    textOrNull(map.get("whyThisQuestion"))));
        }
        return result;
    }

    private void persist(long sessionId, List<PendingDraft> drafts) {
        int seq = 1;
        for (PendingDraft draft : drafts) {
            QuestionDraft content = draft.resolved();
            Map<String, Object> retrieval = new LinkedHashMap<>();
            retrieval.put("source", draft.fromBank() ? SOURCE_BANK : SOURCE_GENERATED);
            retrieval.put("queryLimit", MAX_CANDIDATES);
            retrieval.put("bankQuestionId", draft.bankQuestionId());
            retrieval.put("score", draft.score());
            questionRepository.insert(new InterviewQuestion(
                    0,
                    sessionId,
                    seq,
                    draft.slot.dimension(),
                    draft.slot.difficulty(),
                    content.text(),
                    draft.fromBank() ? SOURCE_BANK : SOURCE_GENERATED,
                    draft.bankQuestionId(),
                    content.referencePoints(),
                    content.whyThisQuestion(),
                    jsonCodec.write(retrieval),
                    jsonCodec.write(Map.of("dimension", draft.slot.dimension(), "seq", seq)),
                    "PENDING"));
            seq++;
        }
    }

    private static String textOrNull(Object value) {
        return value instanceof String text && !text.isBlank() ? text.strip() : null;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    /** AI 生成题草稿。 */
    private record AiDraft(
            String dimension, String difficulty, String text, List<String> referencePoints, String whyThisQuestion) {}

    /** 槽位草稿：题库命中或待生成。 */
    private static final class PendingDraft {

        private final QuestionPlan.Slot slot;
        private final RetrievedQuestion bank;
        private QuestionDraft resolved;

        private PendingDraft(QuestionPlan.Slot slot, RetrievedQuestion bank) {
            this.slot = slot;
            this.bank = bank;
        }

        static PendingDraft fromBank(QuestionPlan.Slot slot, RetrievedQuestion question, QuestionContext context) {
            PendingDraft draft = new PendingDraft(slot, question);
            List<String> points = question.referencePoints().isEmpty()
                    ? List.of("背景与目标", "关键行动", "量化结果")
                    : question.referencePoints();
            String why = "题库命中该岗位题目，考察" + label(slot.dimension())
                    + (context.keywords().isEmpty()
                            ? ""
                            : "，与 JD/简历中的「" + context.keywords().get(0) + "」相关");
            draft.resolved = new QuestionDraft(question.stem(), points, why);
            return draft;
        }

        static PendingDraft missing(QuestionPlan.Slot slot) {
            return new PendingDraft(slot, null);
        }

        boolean fromBank() {
            return bank != null;
        }

        Long bankQuestionId() {
            return bank == null ? null : bank.id();
        }

        Double score() {
            return bank == null ? null : bank.score();
        }

        void fill(AiDraft ai) {
            resolved = new QuestionDraft(ai.text(), ai.referencePoints(), ai.whyThisQuestion());
        }

        void fill(TemplateQuestionFactory.Draft template) {
            resolved = new QuestionDraft(template.text(), template.referencePoints(), template.whyThisQuestion());
        }

        QuestionDraft resolved() {
            return resolved;
        }

        private static String label(String dimension) {
            return switch (dimension) {
                case "DEPTH" -> "专业深度";
                case "PROJECT" -> "项目经验";
                case "BEHAVIOR" -> "行为协作";
                case "FIT" -> "岗位匹配";
                default -> "综合能力";
            };
        }
    }

    private record QuestionDraft(String text, List<String> referencePoints, String whyThisQuestion) {}
}
