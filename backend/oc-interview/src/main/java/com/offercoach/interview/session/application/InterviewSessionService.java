package com.offercoach.interview.session.application;

import com.offercoach.common.error.BizException;
import com.offercoach.common.error.ErrorCode;
import com.offercoach.identity.application.quota.QuotaResult;
import com.offercoach.identity.application.quota.QuotaService;
import com.offercoach.identity.domain.quota.QuotaType;
import com.offercoach.interview.jd.application.JdWebImportService;
import com.offercoach.interview.jd.application.port.JobDescriptionRepository;
import com.offercoach.interview.jd.domain.JobDescription;
import com.offercoach.interview.report.application.ReportGenerationWorker;
import com.offercoach.interview.report.application.port.InterviewReportRepository;
import com.offercoach.interview.report.domain.InterviewReport;
import com.offercoach.interview.resume.application.port.ResumeRepository;
import com.offercoach.interview.resume.domain.Resume;
import com.offercoach.interview.resume.domain.ResumeStatus;
import com.offercoach.interview.session.application.port.InterviewAnswerRepository;
import com.offercoach.interview.session.application.port.InterviewQuestionRepository;
import com.offercoach.interview.session.application.port.InterviewSessionRepository;
import com.offercoach.interview.session.domain.InterviewAnswer;
import com.offercoach.interview.session.domain.InterviewQuestion;
import com.offercoach.interview.session.domain.InterviewSession;
import com.offercoach.interview.session.domain.SessionConfig;
import com.offercoach.interview.session.domain.SessionStateMachine;
import com.offercoach.interview.session.domain.SessionStatus;
import com.offercoach.interview.support.JsonCodec;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 会话用例编排：创建、开始、查询、暂停/恢复、放弃与结束。
 * 创建在单事务内完成额度扣减、每日名额占用与会话落库；幂等重放在事务外读取首次结果。
 */
@Service
public class InterviewSessionService {

    private static final Logger log = LoggerFactory.getLogger(InterviewSessionService.class);

    private static final Set<String> DIFFICULTIES = Set.of("EASY", "MEDIUM", "HARD", "MIXED");
    private static final String ANSWER_MODE_TEXT = "TEXT";
    private static final int MIN_QUESTIONS = 1;
    private static final int MAX_QUESTIONS = 20;
    private static final int MIN_TIME_LIMIT = 60;
    private static final int MAX_TIME_LIMIT = 900;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_IDEMPOTENCY_KEY = 128;
    private static final long START_ESTIMATED_READY_MS = 9000L;
    private static final long REPORT_ESTIMATED_READY_MS = 5000L;
    private static final String REF_TYPE = "INTERVIEW_SESSION";
    private static final String CHARGE_REASON = "SESSION_CHARGE";
    private static final String REFUND_REASON = "SESSION_REFUND";

    private final InterviewSessionRepository sessionRepository;
    private final InterviewQuestionRepository questionRepository;
    private final InterviewAnswerRepository answerRepository;
    private final ResumeRepository resumeRepository;
    private final JobDescriptionRepository jdRepository;
    private final InterviewReportRepository reportRepository;
    private final ReportGenerationWorker reportGenerationWorker;
    private final QuotaService quotaService;
    private final JsonCodec jsonCodec;
    private final JdWebImportService jdWebImportService;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;

    public InterviewSessionService(
            InterviewSessionRepository sessionRepository,
            InterviewQuestionRepository questionRepository,
            InterviewAnswerRepository answerRepository,
            ResumeRepository resumeRepository,
            JobDescriptionRepository jdRepository,
            InterviewReportRepository reportRepository,
            ReportGenerationWorker reportGenerationWorker,
            QuotaService quotaService,
            JsonCodec jsonCodec,
            JdWebImportService jdWebImportService,
            ApplicationEventPublisher eventPublisher,
            PlatformTransactionManager transactionManager) {
        this.sessionRepository = sessionRepository;
        this.questionRepository = questionRepository;
        this.answerRepository = answerRepository;
        this.resumeRepository = resumeRepository;
        this.jdRepository = jdRepository;
        this.reportRepository = reportRepository;
        this.reportGenerationWorker = reportGenerationWorker;
        this.quotaService = quotaService;
        this.jsonCodec = jsonCodec;
        this.jdWebImportService = jdWebImportService;
        this.eventPublisher = eventPublisher;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * 创建会话：幂等键必填；同用户同键返回首次结果，同键不同体返回 DUPLICATE_REQUEST。
     */
    public SessionCreated create(long userId, long resumeId, long jdId, SessionConfig config, String idempotencyKey) {
        return create(userId, resumeId, jdId, null, config, idempotencyKey);
    }

    /**
     * 创建会话：幂等键必填；同用户同键返回首次结果，同键不同体返回 DUPLICATE_REQUEST。
     *
     * <p>岗位来源二选一：已有 JD（{@code jdId > 0}）或联网获取的预览令牌（{@code previewToken}）。
     * 预览令牌在事务内落库为 JD，再据此建会话。</p>
     */
    public SessionCreated create(
            long userId, long resumeId, long jdId, String previewToken, SessionConfig config, String idempotencyKey) {
        String key = normalizeKey(idempotencyKey);
        validateConfig(config);
        Resume resume = loadReadyResume(userId, resumeId);
        boolean hasJd = jdId > 0;
        boolean hasPreview = previewToken != null && !previewToken.isBlank();
        if (!hasJd && !hasPreview) {
            throw new BizException(
                    ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of("jdId", "请选择已有 JD 或先获取目标岗位 JD")));
        }
        if (hasJd) {
            loadOwnedJd(userId, jdId);
        }

        Optional<InterviewSession> existing = sessionRepository.findByUserAndKey(userId, key);
        if (existing.isPresent()) {
            return replay(userId, existing.get(), resume.id(), hasJd ? jdId : null, config);
        }
        try {
            return transactionTemplate.execute(status -> {
                long effectiveJdId = hasJd ? jdId : jdWebImportService.materialize(userId, previewToken);
                return doCreate(userId, resume.id(), effectiveJdId, config, key);
            });
        } catch (DuplicateKeyException exception) {
            Optional<InterviewSession> raced = sessionRepository.findByUserAndKey(userId, key);
            if (raced.isPresent()) {
                return replay(userId, raced.get(), resume.id(), hasJd ? jdId : null, config);
            }
            throw new BizException(ErrorCode.STATE_CONFLICT, Map.of("reason", "ACTIVE_SESSION"));
        }
    }

    private SessionCreated doCreate(long userId, long resumeId, long jdId, SessionConfig config, String key) {
        if (sessionRepository.existsActive(userId)) {
            throw new BizException(ErrorCode.STATE_CONFLICT, Map.of("reason", "ACTIVE_SESSION"));
        }
        long sessionId = sessionRepository.insert(new InterviewSession(
                0,
                userId,
                resumeId,
                jdId,
                SessionStatus.CREATED,
                jsonCodec.write(config.toMap()),
                config.questionCount(),
                0,
                1,
                null,
                null,
                key,
                null,
                null,
                null,
                null));
        QuotaResult quota = quotaService.charge(
                userId, QuotaType.INTERVIEW, 1, CHARGE_REASON, REF_TYPE, sessionId, "interview-charge:" + sessionId);
        quotaService.reserveDailySession(userId);
        return new SessionCreated(
                sessionId,
                SessionStatus.CREATED,
                config.questionCount(),
                quota.charged() ? 1 : 0,
                quota.balanceAfter(),
                OffsetDateTime.now(java.time.ZoneOffset.UTC));
    }

    /**
     * 开始出题：仅 CREATED 可开始，置 PREPARING 后发布事务提交事件。
     */
    @Transactional
    public Started start(long userId, long sessionId) {
        InterviewSession session = loadOwned(userId, sessionId);
        if (!SessionStatus.CREATED.equals(session.status())) {
            throw new BizException(ErrorCode.STATE_CONFLICT, Map.of("status", session.status()));
        }
        SessionStateMachine.require(session.status(), SessionStatus.PREPARING);
        if (!sessionRepository.markPreparing(sessionId)) {
            throw new BizException(ErrorCode.STATE_CONFLICT, Map.of("status", reloadStatus(sessionId)));
        }
        eventPublisher.publishEvent(new SessionPreparingRequested(sessionId, userId));
        return new Started(sessionId, SessionStatus.PREPARING, START_ESTIMATED_READY_MS);
    }

    public SessionPage list(long userId, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        long total = sessionRepository.count(userId);
        long offset = (long) (safePage - 1) * safeSize;
        List<SessionSummary> items = offset >= total
                ? List.of()
                : sessionRepository.list(userId, (int) offset, safeSize).stream()
                        .map(InterviewSessionService::toSummary)
                        .toList();
        return new SessionPage(items, total, safePage, safeSize);
    }

    public SessionView detail(long userId, long sessionId) {
        return toView(loadOwned(userId, sessionId));
    }

    /**
     * 题目列表：READY 首次访问自动进入 IN_PROGRESS，只返回当前及已答/已跳过题目。
     */
    public List<QuestionView> questions(long userId, long sessionId) {
        InterviewSession session = loadOwned(userId, sessionId);
        if (SessionStatus.READY.equals(session.status())) {
            sessionRepository.markInProgress(sessionId);
            questionRepository.markAsked(sessionId, session.currentSeq());
            session = loadOwned(userId, sessionId);
        }
        Map<Long, InterviewAnswer> answerByQuestion = answerRepository.listBySession(sessionId).stream()
                .collect(java.util.stream.Collectors.toMap(
                        InterviewAnswer::questionId, answer -> answer, (first, second) -> first));
        return questionRepository.listVisible(sessionId, session.currentSeq()).stream()
                .map(question -> toQuestionView(question, answerByQuestion.get(question.id())))
                .toList();
    }

    /**
     * 跳过当前题：仅 IN_PROGRESS，计 0 分并推进 currentSeq。
     */
    @Transactional
    public void skip(long userId, long sessionId, long questionId) {
        InterviewSession session = loadOwned(userId, sessionId);
        if (!SessionStatus.IN_PROGRESS.equals(session.status())) {
            throw new BizException(ErrorCode.STATE_CONFLICT, Map.of("status", session.status()));
        }
        InterviewQuestion question = questionRepository
                .findOwned(sessionId, questionId)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("questionId", questionId)));
        if (question.seq() != session.currentSeq()) {
            throw new BizException(ErrorCode.STATE_CONFLICT, Map.of("reason", "NOT_CURRENT_QUESTION"));
        }
        if (!questionRepository.markSkipped(questionId)) {
            throw new BizException(ErrorCode.STATE_CONFLICT, Map.of("reason", "QUESTION_CLOSED"));
        }
        if (!sessionRepository.advanceAfterSkip(sessionId)) {
            throw new BizException(ErrorCode.STATE_CONFLICT, Map.of("status", reloadStatus(sessionId)));
        }
        questionRepository.markAsked(sessionId, session.currentSeq() + 1);
    }

    public void pause(long userId, long sessionId) {
        InterviewSession session = loadOwned(userId, sessionId);
        SessionStateMachine.require(session.status(), SessionStatus.PAUSED);
        if (!sessionRepository.pause(sessionId)) {
            throw new BizException(ErrorCode.STATE_CONFLICT, Map.of("status", reloadStatus(sessionId)));
        }
    }

    public void resume(long userId, long sessionId) {
        InterviewSession session = loadOwned(userId, sessionId);
        SessionStateMachine.require(session.status(), SessionStatus.IN_PROGRESS);
        if (!sessionRepository.resume(sessionId)) {
            throw new BizException(ErrorCode.STATE_CONFLICT, Map.of("status", reloadStatus(sessionId)));
        }
    }

    /**
     * 主动放弃：未提交任何回答时幂等退款；已作答不退。
     */
    public void abandon(long userId, long sessionId) {
        InterviewSession session = loadOwned(userId, sessionId);
        SessionStateMachine.require(session.status(), SessionStatus.ABANDONED);
        if (!sessionRepository.abandon(sessionId)) {
            throw new BizException(ErrorCode.STATE_CONFLICT, Map.of("status", reloadStatus(sessionId)));
        }
        // 题目已生成即视为已消耗出题成本，放弃不再退款，避免“生成后放弃”刷额度。
        if (answerRepository.countBySession(sessionId) == 0 && questionRepository.countBySession(sessionId) == 0) {
            refundQuietly(userId, sessionId);
        }
    }

    /**
     * 结束会话：至少 1 题已答，事务内置 REPORT_GENERATING 并创建报告。
     */
    public Finished finish(long userId, long sessionId) {
        InterviewSession session = loadOwned(userId, sessionId);
        if (session.answeredCount() < 1) {
            throw new BizException(ErrorCode.STATE_CONFLICT, Map.of("reason", "NO_ANSWER"));
        }
        SessionStateMachine.require(session.status(), SessionStatus.REPORT_GENERATING);
        Finished finished = transactionTemplate.execute(status -> {
            if (!sessionRepository.finish(sessionId)) {
                throw new BizException(ErrorCode.STATE_CONFLICT, Map.of("status", reloadStatus(sessionId)));
            }
            long reportId = reportRepository.insertIfAbsent(sessionId, userId);
            String reportStatus = "GENERATING";
            if (reportId == 0) {
                InterviewReport existing = reportRepository
                        .findBySession(sessionId)
                        .orElseThrow(() -> new BizException(ErrorCode.INTERNAL_ERROR));
                reportId = existing.id();
                reportStatus = existing.status();
            }
            log.info("会话结束，报告已排队：sessionId={} reportId={}", sessionId, reportId);
            return new Finished(reportId, reportStatus, REPORT_ESTIMATED_READY_MS);
        });
        if (finished != null) {
            reportGenerationWorker.kick(finished.reportId());
        }
        return finished;
    }

    /** 出题 worker 完成后置 READY；若会话已被放弃则忽略。 */
    public void markReady(long sessionId) {
        if (!sessionRepository.markReady(sessionId)) {
            log.info("会话已不在 PREPARING，忽略出题完成：sessionId={}", sessionId);
        }
    }

    /** 出题失败置 FAILED 并幂等退款。 */
    public void markGenerationFailed(long userId, long sessionId, String reason) {
        try {
            if (!sessionRepository.markGenerationFailed(sessionId, reason)) {
                return;
            }
        } catch (RuntimeException exception) {
            log.warn(
                    "会话置 FAILED 失败：sessionId={} reason={}",
                    sessionId,
                    exception.getClass().getSimpleName());
            return;
        }
        refundQuietly(userId, sessionId);
    }

    public void refundQuietly(long userId, long sessionId) {
        try {
            quotaService.refund(
                    userId,
                    QuotaType.INTERVIEW,
                    1,
                    REFUND_REASON,
                    REF_TYPE,
                    sessionId,
                    "interview-refund:" + sessionId);
        } catch (RuntimeException exception) {
            log.warn(
                    "会话退款未完成：sessionId={} reason={}",
                    sessionId,
                    exception.getClass().getSimpleName());
        }
    }

    public InterviewSession loadOwned(long userId, long sessionId) {
        return sessionRepository
                .findOwned(userId, sessionId)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("sessionId", sessionId)));
    }

    public SessionConfig configOf(InterviewSession session) {
        try {
            return SessionConfig.fromMap(jsonCodec.readMap(session.configJson()), session.questionCount());
        } catch (IllegalArgumentException exception) {
            return SessionConfig.fromMap(Map.of(), session.questionCount());
        }
    }

    public SessionView toView(InterviewSession session) {
        int skipped = Math.toIntExact(questionRepository.countByStatus(session.id(), "SKIPPED"));
        JobDescription jd =
                jdRepository.findOwned(session.userId(), session.jdId()).orElse(null);
        InterviewReport report = reportRepository.findBySession(session.id()).orElse(null);
        return new SessionView(
                session.id(),
                session.status(),
                configOf(session),
                session.answeredCount(),
                skipped,
                session.questionCount(),
                session.currentSeq(),
                session.totalScore(),
                jd == null ? null : jd.title(),
                jd == null ? null : jd.company(),
                report == null ? null : Long.toString(report.id()),
                session.startedAt(),
                session.lastActiveAt(),
                session.createdAt());
    }

    static SessionSummary toSummary(InterviewSession session) {
        return new SessionSummary(
                session.id(),
                session.status(),
                session.questionCount(),
                session.answeredCount(),
                session.currentSeq(),
                session.totalScore(),
                session.resumeId(),
                session.jdId(),
                session.createdAt(),
                session.lastActiveAt());
    }

    static QuestionView toQuestionView(InterviewQuestion question, InterviewAnswer answer) {
        return new QuestionView(
                question.id(),
                question.seq(),
                question.dimension(),
                question.difficulty(),
                question.text(),
                question.source(),
                question.referencePoints(),
                question.status(),
                question.whyThisQuestion(),
                answer == null ? null : answer.answerText(),
                answer == null ? null : answer.durationMs(),
                answer == null ? null : Long.toString(answer.id()));
    }

    private SessionCreated replay(
            long userId, InterviewSession existing, long resumeId, Long expectedJdId, SessionConfig config) {
        boolean same = existing.resumeId() == resumeId
                && (expectedJdId == null || existing.jdId() == expectedJdId)
                && configOf(existing).equals(config);
        if (!same) {
            throw new BizException(ErrorCode.DUPLICATE_REQUEST);
        }
        return new SessionCreated(
                existing.id(),
                existing.status(),
                existing.questionCount(),
                1,
                currentInterviewBalance(userId),
                existing.createdAt());
    }

    private int currentInterviewBalance(long userId) {
        try {
            return quotaService.snapshot(userId).interviewCredits();
        } catch (RuntimeException exception) {
            return 0;
        }
    }

    private Resume loadReadyResume(long userId, long resumeId) {
        Resume resume = resumeRepository
                .findOwned(userId, resumeId)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("resumeId", resumeId)));
        if (!ResumeStatus.READY.equals(resume.parseStatus())) {
            throw new BizException(ErrorCode.STATE_CONFLICT, Map.of("parseStatus", resume.parseStatus()));
        }
        return resume;
    }

    private JobDescription loadOwnedJd(long userId, long jdId) {
        return jdRepository
                .findOwned(userId, jdId)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("jdId", jdId)));
    }

    private String reloadStatus(long sessionId) {
        return sessionRepository
                .findAny(sessionId)
                .map(InterviewSession::status)
                .orElse(null);
    }

    private static String normalizeKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of("Idempotency-Key", "幂等键必填")));
        }
        String key = idempotencyKey.strip();
        if (key.length() > MAX_IDEMPOTENCY_KEY) {
            throw new BizException(
                    ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of("Idempotency-Key", "长度需 <= 128")));
        }
        return key;
    }

    private static void validateConfig(SessionConfig config) {
        if (config == null) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of("config", "配置必填")));
        }
        if (config.questionCount() < MIN_QUESTIONS || config.questionCount() > MAX_QUESTIONS) {
            throw new BizException(
                    ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of("questionCount", "题目数量需为 1-20")));
        }
        if (config.difficulty() == null || !DIFFICULTIES.contains(config.difficulty())) {
            throw new BizException(
                    ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of("difficulty", "难度需为 EASY/MEDIUM/HARD/MIXED")));
        }
        if (!ANSWER_MODE_TEXT.equals(config.answerMode())) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of("answerMode", "当前仅支持 TEXT")));
        }
        if (config.timeLimitSec() < MIN_TIME_LIMIT || config.timeLimitSec() > MAX_TIME_LIMIT) {
            throw new BizException(
                    ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of("timeLimitSec", "时限需为 60-900 秒")));
        }
        if (config.interviewerStyle() == null || config.interviewerStyle().isBlank()) {
            throw new BizException(
                    ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of("interviewerStyle", "面试官风格必填")));
        }
        if (config.language() == null || config.language().isBlank()) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of("language", "语言必填")));
        }
    }

    public record SessionCreated(
            long id, String status, int questionCount, int charged, int balance, OffsetDateTime createdAt) {}

    public record Started(long id, String status, long estimatedReadyInMs) {}

    public record Finished(long reportId, String status, long estimatedReadyInMs) {}

    public record SessionSummary(
            long id,
            String status,
            int questionCount,
            int answeredCount,
            int currentSeq,
            BigDecimal totalScore,
            long resumeId,
            long jdId,
            OffsetDateTime createdAt,
            OffsetDateTime lastActiveAt) {}

    public record SessionPage(List<SessionSummary> items, long total, int page, int size) {}

    public record SessionView(
            long id,
            String status,
            SessionConfig config,
            int answered,
            int skipped,
            int total,
            int currentSeq,
            BigDecimal totalScore,
            String jdTitle,
            String jdCompany,
            String reportId,
            OffsetDateTime startedAt,
            OffsetDateTime lastActiveAt,
            OffsetDateTime createdAt) {}

    public record QuestionView(
            long id,
            int seq,
            String dimension,
            String difficulty,
            String text,
            String source,
            List<String> referencePoints,
            String status,
            String whyThisQuestion,
            String answerText,
            Integer durationMs,
            String answerId) {}
}
