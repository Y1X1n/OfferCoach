package com.offercoach.interview.report.application;

import com.offercoach.interview.feedback.application.port.AnswerFeedbackRepository;
import com.offercoach.interview.feedback.domain.AnswerFeedback;
import com.offercoach.interview.session.application.port.InterviewAnswerRepository;
import com.offercoach.interview.session.application.port.InterviewQuestionRepository;
import com.offercoach.interview.session.domain.InterviewAnswer;
import com.offercoach.interview.session.domain.InterviewQuestion;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/**
 * 报告详情的逐题素材：题目原文、回答原文与最新一版反馈。
 *
 * <p>必须按会话批量取：逐题查时每个 breakdown 项要发三条 SQL，20 题就是 60 条，而报告详情
 * 只有一份会话，三张表都能按 {@code session_id} 一次取全。</p>
 */
@Component
public class ReportBreakdownResolver {

    private final InterviewQuestionRepository questionRepository;
    private final InterviewAnswerRepository answerRepository;
    private final AnswerFeedbackRepository feedbackRepository;

    public ReportBreakdownResolver(
            InterviewQuestionRepository questionRepository,
            InterviewAnswerRepository answerRepository,
            AnswerFeedbackRepository feedbackRepository) {
        this.questionRepository = questionRepository;
        this.answerRepository = answerRepository;
        this.feedbackRepository = feedbackRepository;
    }

    /**
     * 一次取齐该会话的题目、回答与反馈；未找到的条目在 {@link Sources} 里就是 null。
     */
    public Sources resolve(long userId, long sessionId) {
        Map<Long, String> questionTexts = new LinkedHashMap<>();
        for (InterviewQuestion question : questionRepository.listBySession(sessionId)) {
            questionTexts.put(question.id(), question.text());
        }
        Map<Long, String> answerTexts = new LinkedHashMap<>();
        for (InterviewAnswer answer : answerRepository.listOwnedBySession(userId, sessionId)) {
            answerTexts.put(answer.id(), answer.answerText());
        }
        Map<Long, AnswerFeedback> feedbacks =
                index(feedbackRepository.listLatestBySession(userId, sessionId), AnswerFeedback::answerId);
        return new Sources(questionTexts, answerTexts, feedbacks);
    }

    private static <T> Map<Long, T> index(Collection<T> rows, Function<T, Long> key) {
        Map<Long, T> map = new LinkedHashMap<>();
        for (T row : rows) {
            map.put(key.apply(row), row);
        }
        return map;
    }

    /**
     * 逐题素材；查询结果按 id 索引，缺失即 null（报告可能早于题面/回答被清理）。
     */
    public record Sources(
            Map<Long, String> questionTexts, Map<Long, String> answerTexts, Map<Long, AnswerFeedback> feedbacks) {

        /** 没有逐题拆解时的空素材：一次查询都不发。 */
        public static Sources empty() {
            return new Sources(Map.of(), Map.of(), Map.of());
        }

        public String questionText(long questionId) {
            return questionTexts.get(questionId);
        }

        public String answerText(long answerId) {
            return answerTexts.get(answerId);
        }

        public AnswerFeedback feedback(long answerId) {
            return feedbacks.get(answerId);
        }
    }
}
