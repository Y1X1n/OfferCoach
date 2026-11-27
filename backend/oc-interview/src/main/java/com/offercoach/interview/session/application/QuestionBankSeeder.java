package com.offercoach.interview.session.application;

import com.offercoach.interview.qbank.QuestionBankMapper;
import com.offercoach.interview.session.application.QuestionContextBuilder.QuestionContext;
import com.offercoach.interview.support.JsonCodec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 行业题库沉淀：把本次由「搜索线索 + 模型」生成的题写入 question_bank，状态为 DRAFT。
 *
 * <p>落 DRAFT 而不是直接 PUBLISHED：模型生成内容需要人工审核后才能进入召回池，
 * 避免低质题污染题库。按 (来源 ai-search, 岗位族, 题干) 去重，重复生成不重复入库。</p>
 */
@Service
public class QuestionBankSeeder {

    private static final Logger log = LoggerFactory.getLogger(QuestionBankSeeder.class);
    private static final String SOURCE_REPO = "ai-search";
    private static final int TITLE_LIMIT = 200;
    private static final int MAX_TAGS = 5;

    private final QuestionBankMapper mapper;
    private final JsonCodec jsonCodec;

    public QuestionBankSeeder(QuestionBankMapper mapper, JsonCodec jsonCodec) {
        this.mapper = mapper;
        this.jsonCodec = jsonCodec;
    }

    /**
     * 尽力而为地沉淀题目；失败只告警，不影响本次出题结果。
     */
    public int seed(long userId, QuestionContext context, List<SeedQuestion> questions) {
        if (questions == null || questions.isEmpty()) {
            return 0;
        }
        String jobFamily = context == null
                        || context.jobFamily() == null
                        || context.jobFamily().isBlank()
                ? "通用"
                : context.jobFamily();
        String tags = tags(context);
        int inserted = 0;
        for (SeedQuestion question : questions) {
            if (question == null || question.stem() == null || question.stem().isBlank()) {
                continue;
            }
            try {
                inserted += mapper.insertGenerated(
                        truncate(question.stem().strip(), TITLE_LIMIT),
                        jobFamily,
                        question.dimension(),
                        question.difficulty(),
                        tags,
                        question.stem().strip(),
                        scoringPoints(question.points()),
                        SOURCE_REPO,
                        jobFamily,
                        blankToNull(question.sourceUrl()),
                        userId);
            } catch (RuntimeException exception) {
                log.warn(
                        "题库沉淀失败：family={} reason={}",
                        jobFamily,
                        exception.getClass().getSimpleName());
                return inserted;
            }
        }
        if (inserted > 0) {
            log.info("题库沉淀完成：family={} inserted={}", jobFamily, inserted);
        }
        return inserted;
    }

    private String scoringPoints(List<String> points) {
        List<Map<String, Object>> items = new ArrayList<>();
        if (points != null) {
            double weight = points.isEmpty() ? 0 : Math.round(100.0 / points.size()) / 100.0;
            for (String point : points) {
                if (point == null || point.isBlank()) {
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("point", point.strip());
                item.put("weight", weight);
                items.add(item);
            }
        }
        return jsonCodec.write(items);
    }

    private static String tags(QuestionContext context) {
        if (context == null || context.keywords() == null || context.keywords().isEmpty()) {
            return "";
        }
        return String.join(
                "\u001f",
                context.keywords().stream()
                        .filter(keyword -> keyword != null && !keyword.isBlank())
                        .limit(MAX_TAGS)
                        .map(String::strip)
                        .toList());
    }

    private static String truncate(String value, int limit) {
        return value.length() <= limit ? value : value.substring(0, limit);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    /** 待沉淀的生成题；sourceUrl 为本次检索命中的参考链接。 */
    public record SeedQuestion(
            String dimension, String difficulty, String stem, List<String> points, String why, String sourceUrl) {}
}
