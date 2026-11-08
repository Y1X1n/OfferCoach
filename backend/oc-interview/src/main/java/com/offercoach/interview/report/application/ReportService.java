package com.offercoach.interview.report.application;

import com.offercoach.common.error.BizException;
import com.offercoach.common.error.ErrorCode;
import com.offercoach.interview.feedback.application.port.AnswerFeedbackRepository;
import com.offercoach.interview.feedback.domain.AnswerFeedback;
import com.offercoach.interview.report.application.port.InterviewReportRepository;
import com.offercoach.interview.report.domain.InterviewReport;
import com.offercoach.interview.session.application.port.InterviewAnswerRepository;
import com.offercoach.interview.session.application.port.InterviewQuestionRepository;
import com.offercoach.interview.session.application.port.InterviewSessionRepository;
import com.offercoach.interview.session.domain.InterviewAnswer;
import com.offercoach.interview.session.domain.InterviewQuestion;
import com.offercoach.interview.session.domain.InterviewSession;
import com.offercoach.interview.support.JsonCodec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 报告查询、重试与聚合生成；分数由反馈维度加权，文案可确定性生成。
 */
@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);
    private static final List<String> DIMENSIONS = List.of("content", "structure", "depth", "fit", "language");
    private static final Map<String, String> LABELS = Map.of(
            "content", "内容相关性",
            "structure", "结构化表达",
            "depth", "专业深度",
            "fit", "岗位匹配",
            "language", "语言表达");
    private static final Map<String, Double> WEIGHTS = Map.of(
            "content", 0.25,
            "structure", 0.20,
            "depth", 0.25,
            "fit", 0.15,
            "language", 0.15);
    private static final int MAX_PAGE_SIZE = 100;

    private final InterviewReportRepository reportRepository;
    private final AnswerFeedbackRepository feedbackRepository;
    private final InterviewAnswerRepository answerRepository;
    private final InterviewQuestionRepository questionRepository;
    private final InterviewSessionRepository sessionRepository;
    private final JsonCodec jsonCodec;

    public ReportService(
            InterviewReportRepository reportRepository,
            AnswerFeedbackRepository feedbackRepository,
            InterviewAnswerRepository answerRepository,
            InterviewQuestionRepository questionRepository,
            InterviewSessionRepository sessionRepository,
            JsonCodec jsonCodec) {
        this.reportRepository = reportRepository;
        this.feedbackRepository = feedbackRepository;
        this.answerRepository = answerRepository;
        this.questionRepository = questionRepository;
        this.sessionRepository = sessionRepository;
        this.jsonCodec = jsonCodec;
    }

    @Transactional(readOnly = true)
    public ReportPage list(long userId, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        long total = reportRepository.count(userId);
        long offset = (long) (safePage - 1) * safeSize;
        List<InterviewReport> items =
                offset >= total ? List.of() : reportRepository.list(userId, (int) offset, safeSize);
        return new ReportPage(items, total, safePage, safeSize);
    }

    @Transactional(readOnly = true)
    public InterviewReport detail(long userId, long reportId) {
        return reportRepository
                .findOwned(userId, reportId)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("reportId", reportId)));
    }

    /**
     * 仅 FAILED 可重试；重置 attempts 并让会话回到 REPORT_GENERATING。
     */
    @Transactional
    public void retry(long userId, long reportId) {
        InterviewReport report = detail(userId, reportId);
        if (!"FAILED".equals(report.status())) {
            throw new BizException(ErrorCode.STATE_CONFLICT, Map.of("status", report.status()));
        }
        if (!reportRepository.resetForRetry(userId, reportId)) {
            throw new BizException(ErrorCode.STATE_CONFLICT);
        }
        sessionRepository.markReportGenerating(report.sessionId());
    }

    /**
     * 聚合反馈生成报告；由 worker 在抢占成功后调用。
     */
    @Transactional
    public void generate(long reportId) {
        InterviewReport report = reportRepository
                .findById(reportId)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("reportId", reportId)));
        if (!"GENERATING".equals(report.status())) {
            return;
        }
        InterviewSession session = sessionRepository
                .findAny(report.sessionId())
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND));
        List<AnswerFeedback> feedbacks = feedbackRepository.listBySession(session.id());
        List<InterviewAnswer> answers = answerRepository.listBySession(session.id());
        if (feedbacks.isEmpty()) {
            throw new IllegalStateException("没有可聚合的评分");
        }
        Set<Long> feedbackAnswerIds = new java.util.HashSet<>();
        for (AnswerFeedback feedback : feedbacks) {
            feedbackAnswerIds.add(feedback.answerId());
        }
        boolean scoringPending = answers.stream()
                .anyMatch(answer -> !feedbackAnswerIds.contains(answer.id())
                        && !"FAILED".equals(answer.status())
                        && !"REJECTED".equals(answer.status()));
        if (scoringPending) {
            throw new ScoringPendingException("评分尚未完成");
        }
        List<InterviewQuestion> questions = questionRepository.listBySession(session.id());
        Map<Long, InterviewQuestion> questionById = new LinkedHashMap<>();
        for (InterviewQuestion question : questions) {
            questionById.put(question.id(), question);
        }
        Map<Long, Long> questionByAnswer = new LinkedHashMap<>();
        for (InterviewAnswer answer : answers) {
            questionByAnswer.put(answer.id(), answer.questionId());
        }
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        for (String dimension : DIMENSIONS) {
            totals.put(dimension, BigDecimal.ZERO);
        }
        for (AnswerFeedback feedback : feedbacks) {
            for (String dimension : DIMENSIONS) {
                BigDecimal value = feedback.dimensions().get(dimension);
                if (value != null) {
                    totals.put(dimension, totals.get(dimension).add(value));
                }
            }
        }
        List<InterviewReport.RadarItem> radar = new ArrayList<>();
        BigDecimal overall = BigDecimal.ZERO;
        for (String dimension : DIMENSIONS) {
            BigDecimal average =
                    totals.get(dimension).divide(BigDecimal.valueOf(feedbacks.size()), 1, RoundingMode.HALF_UP);
            radar.add(new InterviewReport.RadarItem(dimension, LABELS.get(dimension), average.doubleValue()));
            overall = overall.add(average.multiply(BigDecimal.valueOf(WEIGHTS.get(dimension))));
        }
        BigDecimal overallScore = overall.multiply(BigDecimal.TEN).setScale(1, RoundingMode.HALF_UP);

        List<String> highlights = distinctLimit(
                feedbacks.stream()
                        .flatMap(feedback -> feedback.strengths().stream())
                        .toList(),
                4);
        List<String> risks = distinctLimit(
                feedbacks.stream()
                        .flatMap(feedback -> feedback.improvements().stream())
                        .toList(),
                4);
        List<InterviewReport.ImprovementItem> plan = plan(radar, risks);
        List<InterviewReport.BreakdownItem> breakdown = breakdown(feedbacks, questionById, questionByAnswer);
        String summary = summary(overallScore, radar, feedbacks.size());

        reportRepository.markReady(
                reportId,
                overallScore,
                jsonCodec.write(radar),
                summary,
                jsonCodec.write(highlights),
                jsonCodec.write(risks),
                jsonCodec.write(plan),
                jsonCodec.write(breakdown));
        sessionRepository.complete(session.id(), overallScore);
        log.info("报告生成完成：reportId={} sessionId={} score={}", reportId, session.id(), overallScore);
    }

    @Transactional
    public void fail(long reportId, String error, int maxAttempts) {
        int attempts = reportRepository.incrementAttempts(reportId, error);
        if (attempts >= maxAttempts) {
            reportRepository.markFailed(reportId, error);
            reportRepository
                    .findById(reportId)
                    .ifPresent(report -> sessionRepository.markReportFailed(report.sessionId()));
        }
    }

    private static String summary(BigDecimal overall, List<InterviewReport.RadarItem> radar, int count) {
        String strongest = radar.stream()
                .max(Comparator.comparingDouble(InterviewReport.RadarItem::score))
                .map(InterviewReport.RadarItem::label)
                .orElse("综合表现");
        String weakest = radar.stream()
                .min(Comparator.comparingDouble(InterviewReport.RadarItem::score))
                .map(InterviewReport.RadarItem::label)
                .orElse("专业深度");
        return "完成 " + count + " 题，总分 " + overall.toPlainString() + "。" + strongest + "表现相对突出，" + weakest
                + "仍有提升空间。建议按改进计划做专项练习后再次模拟。";
    }

    private static List<InterviewReport.ImprovementItem> plan(
            List<InterviewReport.RadarItem> radar, List<String> risks) {
        List<InterviewReport.RadarItem> sorted = new ArrayList<>(radar);
        sorted.sort(Comparator.comparingDouble(InterviewReport.RadarItem::score));
        List<InterviewReport.ImprovementItem> plan = new ArrayList<>();
        for (int index = 0; index < Math.min(3, sorted.size()); index++) {
            InterviewReport.RadarItem item = sorted.get(index);
            String risk = risks.isEmpty() ? "该维度得分偏低" : risks.get(Math.min(index, risks.size() - 1));
            plan.add(new InterviewReport.ImprovementItem(
                    index + 1, actionOf(item.dimension()), risk, resourceOf(item.dimension())));
        }
        return plan;
    }

    private static String actionOf(String dimension) {
        return switch (dimension) {
            case "content" -> "用 STAR 重写两段项目经历，突出个人职责与结果";
            case "structure" -> "练习先结论后展开，分点控制回答结构";
            case "depth" -> "补充缓存一致性、消息可靠性等方案的对比与取舍";
            case "fit" -> "对照目标岗位 JD，逐条准备技能匹配案例";
            default -> "进行 3 分钟限时口述练习，减少填充词";
        };
    }

    private static String resourceOf(String dimension) {
        return switch (dimension) {
            case "content", "structure" -> "STAR 模板与专项练习";
            case "depth" -> "系统设计专题与错题复盘";
            case "fit" -> "目标岗位 JD 对照清单";
            default -> "限时表达训练";
        };
    }

    private static List<InterviewReport.BreakdownItem> breakdown(
            List<AnswerFeedback> feedbacks,
            Map<Long, InterviewQuestion> questionById,
            Map<Long, Long> questionByAnswer) {
        List<InterviewReport.BreakdownItem> result = new ArrayList<>();
        for (AnswerFeedback feedback : feedbacks) {
            Long questionId = questionByAnswer.get(feedback.answerId());
            InterviewQuestion question = questionId == null ? null : questionById.get(questionId);
            if (question == null) {
                continue;
            }
            String summary = feedback.strengths().isEmpty()
                    ? "本题得分 " + feedback.overallScore().toPlainString()
                    : feedback.strengths().getFirst();
            result.add(new InterviewReport.BreakdownItem(
                    question.id(),
                    feedback.answerId(),
                    feedback.id(),
                    question.seq(),
                    feedback.overallScore().doubleValue(),
                    question.dimension(),
                    summary));
        }
        return result;
    }

    private static List<String> distinctLimit(List<String> items, int limit) {
        Set<String> unique = new LinkedHashSet<>(items);
        return unique.stream()
                .filter(item -> item != null && !item.isBlank())
                .limit(limit)
                .toList();
    }

    public record ReportPage(List<InterviewReport> items, long total, int page, int size) {}

    /**
     * 评分尚未全部完成；属于可等待状态，worker 不计失败次数，由后续触发继续生成。
     */
    public static class ScoringPendingException extends RuntimeException {

        public ScoringPendingException(String message) {
            super(message);
        }
    }
}
