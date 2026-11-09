package com.offercoach.interview.feedback.application;

import com.offercoach.interview.feedback.domain.FeedbackEvidence;
import com.offercoach.interview.jd.domain.SkillDictionary;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 确定性评分：五维 0-10，约 1 位小数；相同输入始终得到相同结果，供无模型时降级。
 *
 * <p>反馈文案按题目场景（项目/技术/行为/岗位匹配）与题型焦点（排查、性能、设计、原理、协作、复盘）
 * 生成，并结合参考答案要点覆盖情况，避免所有题目给出同一套模板意见。</p>
 */
public final class FeedbackScorer {

    private static final Pattern NUMBER = Pattern.compile("\\d+(?:\\.\\d+)?%?");
    private static final Pattern METRIC =
            Pattern.compile("\\d+(?:\\.\\d+)?\\s*(?:ms|毫秒|秒|分钟|小时|%|％|qps|tps|万|亿|gb|mb|kb)", Pattern.CASE_INSENSITIVE);
    private static final List<String> STRUCTURE_MARKERS =
            List.of("首先", "其次", "然后", "最后", "第一", "第二", "背景", "目标", "方案", "结果", "复盘", "总结", "因此", "所以");
    private static final List<String> TRADEOFF_MARKERS = List.of("对比", "取舍", "权衡", "替代", "降级", "失败", "风险");
    private static final List<String> FILLERS = List.of("呃", "那个", "就是说", "然后就是");
    private static final List<String> FIRST_PERSON = List.of(
            "我负责", "负责", "我主导", "主导", "我设计", "我实现", "我排查", "我优化", "我推动", "我参与", "我完成", "我牵头", "我做了", "我的职责", "我判断",
            "我组织", "我列出", "我建议", "我沟通", "我协调", "我安排", "我推进", "我承担", "我先", "我把", "我在", "我按", "我会", "我能", "我作为");
    private static final List<String> RESULT_MARKERS = List.of(
            "提升", "降低", "减少", "下降", "降到", "上升", "提升到", "提高到", "缩短到", "增长", "缩短", "节省", "翻倍", "达到", "上线", "交付", "落地",
            "完成率", "耗时");
    private static final List<String> FAILURE_MARKERS = List.of("失败", "故障", "事故", "踩坑", "回滚", "报错", "线上问题");
    private static final List<String> REFLECTION_MARKERS = List.of("复盘", "反思", "如果重来", "经验教训", "后续改进", "总结");
    private static final List<String> WHY_MARKERS = List.of("因为", "原因", "原理", "机制", "底层", "为什么", "本质上");
    private static final List<String> EDGE_MARKERS =
            List.of("边界", "极端", "并发", "幂等", "降级", "熔断", "兜底", "异常", "超时", "重试", "限流", "容量");
    private static final List<String> CONFLICT_MARKERS = List.of("冲突", "分歧", "说服", "协调", "推动", "对齐", "反对");
    private static final List<String> GOAL_MARKERS = List.of("目标", "背景", "需求", "业务", "场景", "痛点");
    private static final List<String> ACTION_MARKERS =
            List.of("方案", "实现", "设计", "拆分", "落地", "排查", "优化", "改造", "重构", "引入", "组织", "沟通", "跟进", "安排", "列出", "说服");
    private static final List<String> MOTIVATION_MARKERS =
            List.of("为什么选择", "想加入", "看重", "认同", "兴趣", "职业发展", "长期方向", "选择这个岗位");
    private static final List<String> PLAN_MARKERS = List.of("30 天", "60 天", "90 天", "计划", "学习路径", "入职后", "短期目标");
    private static final List<String> TROUBLESHOOT_MARKERS = List.of("故障", "排查", "线上", "事故", "报警", "宕机", "定位");
    private static final List<String> PERFORMANCE_MARKERS = List.of("性能", "优化", "延迟", "并发", "吞吐", "慢查询", "压测");
    private static final List<String> DESIGN_MARKERS = List.of("设计", "架构", "方案", "选型", "拆分", "扩展性");
    private static final List<String> PRINCIPLE_MARKERS = List.of("原理", "为什么", "机制", "底层", "如何工作", "实现原理");
    private static final Map<String, Double> WEIGHTS = Map.of(
            "content", 0.25,
            "structure", 0.20,
            "depth", 0.25,
            "fit", 0.15,
            "language", 0.15);
    private static final int MIN_MEANINGFUL_LENGTH = 20;

    private FeedbackScorer() {}

    public static Result score(Input input) {
        String text = input.answerText() == null ? "" : input.answerText().strip();
        String lower = text.toLowerCase(Locale.ROOT);
        List<String> sentences = sentences(text);
        List<String> skills = SkillDictionary.findSkills(text);
        List<String> domainTerms = SkillDictionary.findDomainTerms(text);
        int numbers = count(NUMBER.matcher(text));
        int metrics = count(METRIC.matcher(text));
        int markers = countMarkers(lower);
        Scenario scenario = scenarioOf(input.dimension());
        Focus focus = focusOf(scenario, lower);
        Analysis analysis = analyze(input, text, lower, sentences, skills, numbers, markers);

        int jdTotal = distinctKeywordCount(input.jdKeywords());
        int jdMatchedCount = jdMatchCount(text, input.jdKeywords());
        double relevance = questionRelevance(input.questionText(), lower);

        double content = contentScore(text, analysis, metrics, relevance, domainTerms);
        double structure = structureScore(text, analysis);
        double depth = depthScore(text, analysis, metrics, domainTerms);
        double fit = fitScore(text, analysis, jdTotal, jdMatchedCount, metrics);
        double language = languageScore(text, analysis);
        if (text.isBlank()) {
            content = 0.0;
            structure = 0.0;
            depth = 0.0;
            fit = jdTotal > 0 ? 0.5 : 1.0;
            language = 0.0;
        } else if (text.length() < MIN_MEANINGFUL_LENGTH) {
            content = Math.min(content, 2.0);
            structure = Math.min(structure, 2.5);
            depth = Math.min(depth, 2.0);
            fit = Math.min(fit, 3.0);
            language = Math.min(language, 2.5);
        } else if (text.length() < 40) {
            content = Math.min(content, 5.0);
            structure = Math.min(structure, 5.0);
            depth = Math.min(depth, 5.0);
            fit = Math.min(fit, 5.0);
            language = Math.min(language, 5.0);
        }
        boolean offTopic = analysis.referenceTotal() > 0
                && analysis.referenceCovered() == 0
                && relevance == 0.0
                && analysis.skills().isEmpty()
                && domainTerms.isEmpty();

        Map<String, BigDecimal> dimensions = new LinkedHashMap<>();
        dimensions.put("content", round(content));
        dimensions.put("structure", round(structure));
        dimensions.put("depth", round(depth));
        dimensions.put("fit", round(fit));
        dimensions.put("language", round(language));

        double weighted = dimensions.entrySet().stream()
                .mapToDouble(entry -> entry.getValue().doubleValue() * WEIGHTS.get(entry.getKey()))
                .sum();
        if (offTopic) {
            weighted = Math.min(weighted, 4.0);
        }
        BigDecimal overall = round(weighted);

        List<String> strengths = strengths(scenario, focus, analysis, dimensions);
        List<String> improvements = improvements(scenario, focus, analysis, dimensions);
        List<FeedbackEvidence> evidence = evidence(scenario, analysis);
        String narrative =
                narrative(scenario, focus, dimensions, overall, strengths, improvements, evidence, input.proPlan());
        String rewritten = input.proPlan() ? rewrite(scenario, text) : null;
        return new Result(overall, dimensions, strengths, improvements, evidence, narrative, rewritten);
    }

    private static Analysis analyze(
            Input input,
            String text,
            String lower,
            List<String> sentences,
            List<String> skills,
            int numbers,
            int markers) {
        List<String> jdMatched = jdMatched(text, input.jdKeywords());
        RefCoverage references = referenceCoverage(text, input.referencePoints());
        return new Analysis(
                text,
                lower,
                input.questionText() == null ? "" : input.questionText().toLowerCase(Locale.ROOT),
                sentences,
                skills,
                jdMatched,
                numbers,
                markers,
                countTradeoffs(lower),
                countFillers(lower),
                containsAny(lower, FIRST_PERSON),
                containsAny(lower, GOAL_MARKERS),
                containsAny(lower, ACTION_MARKERS),
                containsAny(lower, RESULT_MARKERS),
                containsAny(lower, FAILURE_MARKERS),
                containsAny(lower, REFLECTION_MARKERS),
                containsAny(lower, WHY_MARKERS),
                containsAny(lower, EDGE_MARKERS),
                containsAny(lower, CONFLICT_MARKERS),
                containsAny(lower, MOTIVATION_MARKERS),
                containsAny(lower, PLAN_MARKERS),
                references.covered(),
                references.total(),
                references.coveredPhrases(),
                references.missingPhrases());
    }

    private static List<String> jdMatched(String text, List<String> jdKeywords) {
        if (jdKeywords == null || jdKeywords.isEmpty()) {
            return List.of();
        }
        return jdKeywords.stream()
                .filter(keyword -> keyword != null && !keyword.isBlank())
                .filter(keyword -> SkillDictionary.containsSkill(text, keyword)
                        || text.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT)))
                .distinct()
                .limit(4)
                .toList();
    }

    private static RefCoverage referenceCoverage(String text, List<String> points) {
        if (points == null || points.isEmpty()) {
            return new RefCoverage(0, 0, List.of(), List.of());
        }
        String normalizedText = text.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        int total = 0;
        int covered = 0;
        List<String> coveredPhrases = new ArrayList<>();
        List<String> missingPhrases = new ArrayList<>();
        for (String point : points) {
            if (point == null || point.isBlank()) {
                continue;
            }
            total++;
            if (coversPoint(normalizedText, point)) {
                covered++;
                if (coveredPhrases.size() < 3) {
                    coveredPhrases.add(shortPhrase(point));
                }
            } else if (missingPhrases.size() < 3) {
                missingPhrases.add(shortPhrase(point));
            }
        }
        return new RefCoverage(total, covered, coveredPhrases, missingPhrases);
    }

    private static boolean coversPoint(String normalizedText, String point) {
        String normalizedPoint = point.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        if (normalizedText.contains(normalizedPoint)) {
            return true;
        }
        String[] chunks = normalizedPoint.split("[、，,。；;：:（）()/\\[\\]【】]+");
        for (String chunk : chunks) {
            if (chunk.length() >= 3 && chunk.length() <= 16 && normalizedText.contains(chunk)) {
                return true;
            }
            if (chunk.length() > 10) {
                for (String part : chunk.split("[和与及的]+")) {
                    if (part.length() >= 3 && part.length() <= 16 && normalizedText.contains(part)) {
                        return true;
                    }
                }
            }
        }
        return normalizedPoint.length() <= 12 && semanticCovers(normalizedText, normalizedPoint);
    }

    private static boolean semanticCovers(String text, String point) {
        if ((point.contains("职责") || point.contains("负责") || point.contains("个人")) && containsAny(text, FIRST_PERSON)) {
            return true;
        }
        if ((point.contains("取舍") || point.contains("权衡") || point.contains("选型") || point.contains("为什么"))
                && (containsAny(text, TRADEOFF_MARKERS) || containsAny(text, WHY_MARKERS))) {
            return true;
        }
        if ((point.contains("冲突") || point.contains("协作") || point.contains("沟通") || point.contains("推动"))
                && containsAny(text, CONFLICT_MARKERS)) {
            return true;
        }
        if ((point.contains("复盘") || point.contains("反思") || point.contains("改进"))
                && containsAny(text, REFLECTION_MARKERS)) {
            return true;
        }
        if ((point.contains("结果") || point.contains("量化") || point.contains("指标"))
                && (NUMBER.matcher(text).find() || containsAny(text, RESULT_MARKERS))) {
            return true;
        }
        if ((point.contains("经历") || point.contains("项目"))
                && (containsAny(text, ACTION_MARKERS) || containsAny(text, FIRST_PERSON))) {
            return true;
        }
        if ((point.contains("背景") && text.contains("背景")) || (point.contains("目标") && text.contains("目标"))) {
            return true;
        }
        if (point.contains("行动") && containsAny(text, ACTION_MARKERS)) {
            return true;
        }
        if ((point.contains("计划") || point.contains("规划")) && containsAny(text, PLAN_MARKERS)) {
            return true;
        }
        if ((point.contains("动机") || point.contains("意愿")) && containsAny(text, MOTIVATION_MARKERS)) {
            return true;
        }
        if ((point.contains("原理") || point.contains("机制") || point.contains("原因")) && containsAny(text, WHY_MARKERS)) {
            return true;
        }
        if ((point.contains("边界") || point.contains("异常") || point.contains("降级")) && containsAny(text, EDGE_MARKERS)) {
            return true;
        }
        String[] technicalWords = {
            "延迟", "性能", "并发", "缓存", "一致性", "幂等", "事务", "索引", "锁", "消息", "队列", "限流", "熔断", "分库", "分表", "复制", "超时", "重试",
            "降级", "总分", "分项", "摘要", "字段", "类型", "枚举", "范围", "JSON", "Schema", "DTO", "版本", "契约", "校验", "评分规则"
        };
        for (String word : technicalWords) {
            if (point.contains(word) && text.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private static String shortPhrase(String point) {
        String cleaned = point.replaceAll("[。；;，,、：:\\s]+", " ").strip();
        String[] chunks = cleaned.split(" ");
        String candidate = chunks.length == 0 ? cleaned : chunks[0];
        return candidate.length() <= 18 ? candidate : candidate.substring(0, 18) + "…";
    }

    private static Scenario scenarioOf(String dimension) {
        if (dimension == null) {
            return Scenario.TECHNICAL;
        }
        return switch (dimension.toUpperCase(Locale.ROOT)) {
            case "PROJECT" -> Scenario.PROJECT;
            case "BEHAVIOR" -> Scenario.BEHAVIOR;
            case "FIT" -> Scenario.FIT;
            default -> Scenario.TECHNICAL;
        };
    }

    private static Focus focusOf(Scenario scenario, String lower) {
        if (scenario == Scenario.TECHNICAL) {
            if (containsAny(lower, TROUBLESHOOT_MARKERS)) {
                return Focus.TROUBLESHOOT;
            }
            if (containsAny(lower, PERFORMANCE_MARKERS)) {
                return Focus.PERFORMANCE;
            }
            if (containsAny(lower, DESIGN_MARKERS)) {
                return Focus.DESIGN;
            }
            if (containsAny(lower, PRINCIPLE_MARKERS)) {
                return Focus.PRINCIPLE;
            }
            return Focus.GENERAL;
        }
        if (scenario == Scenario.BEHAVIOR) {
            if (containsAny(lower, CONFLICT_MARKERS)) {
                return Focus.COLLABORATION;
            }
            if (containsAny(lower, FAILURE_MARKERS) || containsAny(lower, REFLECTION_MARKERS)) {
                return Focus.REFLECTION;
            }
            return Focus.GENERAL;
        }
        return Focus.GENERAL;
    }

    private static double contentScore(
            String text, Analysis analysis, int metrics, double relevance, List<String> domainTerms) {
        double score = 2.0;
        if (analysis.referenceTotal() > 0) {
            score += 4.0 * analysis.referenceCovered() / (double) analysis.referenceTotal();
        } else {
            score += 2.0;
        }
        score += 1.6 * relevance;
        score += Math.min(1.1, analysis.skills().size() * 0.22 + domainTerms.size() * 0.12);
        score += Math.min(1.0, metrics * 0.4);
        int length = text.length();
        if (length < 40) {
            score -= 1.4;
        } else if (length < 80) {
            score -= 0.6;
        } else if (length >= 120) {
            score += 0.4;
        }
        if (length > 1200) {
            score -= 0.3;
        }
        score -= duplicateSentenceRatio(analysis.sentences()) * 1.5;
        if (analysis.referenceTotal() > 0
                && analysis.referenceCovered() == 0
                && relevance == 0.0
                && analysis.skills().isEmpty()
                && domainTerms.isEmpty()) {
            score = Math.min(score, 3.0);
        }
        return clamp(score);
    }

    private static double structureScore(String text, Analysis analysis) {
        double score = 3.0;
        score += Math.min(2.5, analysis.structureMarkers() * 0.45);
        if (text.split("\\n\\s*\\n").length >= 2) {
            score += 0.6;
        }
        if (hasBulletLines(text)) {
            score += 0.4;
        }
        int sentenceCount = analysis.sentences().size();
        if (sentenceCount >= 3) {
            score += 0.5;
        }
        if (sentenceCount >= 6) {
            score += 0.4;
        }
        if (analysis.goal()) {
            score += 0.4;
        }
        if (analysis.action()) {
            score += 0.3;
        }
        if (analysis.result()) {
            score += 0.4;
        }
        if (analysis.reflection()) {
            score += 0.2;
        }
        if (sentenceCount <= 1 && text.length() >= 200) {
            score -= 1.2;
        }
        if (text.length() < 60) {
            score -= 0.6;
        }
        return clamp(score);
    }

    private static double depthScore(String text, Analysis analysis, int metrics, List<String> domainTerms) {
        double score = 2.0;
        if (analysis.why()) {
            score += 1.2;
        }
        score += Math.min(1.5, analysis.tradeoffs() * 0.5);
        if (analysis.edge()) {
            score += 1.0;
        }
        if (analysis.failure()) {
            score += 0.6;
        }
        if (analysis.reflection()) {
            score += 0.5;
        }
        score += Math.min(1.4, metrics * 0.45);
        score += Math.min(1.2, domainTerms.size() * 0.2);
        score += Math.min(0.8, analysis.skills().size() * 0.15);
        if (text.length() < 60) {
            score -= 0.8;
        }
        return clamp(score);
    }

    private static double fitScore(String text, Analysis analysis, int jdTotal, int jdMatchedCount, int metrics) {
        double score;
        if (jdTotal > 0) {
            double ratio = Math.min(1.0, jdMatchedCount / (double) Math.min(jdTotal, 8));
            score = 4.0 + Math.min(3.5, jdMatchedCount * 0.9) + 2.0 * ratio;
        } else {
            score = 4.0 + Math.min(1.5, analysis.skills().size() * 0.25) + (metrics > 0 ? 0.4 : 0);
        }
        if (analysis.motivation()) {
            score += 0.3;
        }
        if (analysis.plan()) {
            score += 0.2;
        }
        if (text.length() < 40) {
            score -= 0.5;
        }
        return clamp(Math.min(score, 9.0));
    }

    private static double languageScore(String text, Analysis analysis) {
        double score = 5.0;
        int sentenceCount = analysis.sentences().size();
        if (sentenceCount >= 2) {
            score += 0.5;
        }
        if (sentenceCount >= 4) {
            score += 0.5;
        }
        if (sentenceCount > 0) {
            double averageLength = analysis.sentences().stream()
                    .mapToInt(String::length)
                    .average()
                    .orElse(0);
            if (averageLength >= 12 && averageLength <= 80) {
                score += 0.4;
            }
        }
        int length = text.length();
        if (length >= 100 && length <= 800) {
            score += 0.4;
        }
        if (endsWithSentencePunctuation(text)) {
            score += 0.2;
        }
        score -= Math.min(2.0, analysis.fillers() * 0.6);
        score -= duplicateSentenceRatio(analysis.sentences()) * 1.2;
        if (length < 40) {
            score -= 1.2;
        } else if (length < 80) {
            score -= 0.4;
        }
        return clamp(Math.min(score, 9.2));
    }

    private static double questionRelevance(String questionText, String answerLower) {
        if (questionText == null || questionText.isBlank()) {
            return 0.0;
        }
        Set<String> terms = new LinkedHashSet<>();
        for (String skill : SkillDictionary.findSkills(questionText)) {
            terms.add(skill.toLowerCase(Locale.ROOT));
        }
        for (String term : SkillDictionary.findDomainTerms(questionText)) {
            terms.add(term.toLowerCase(Locale.ROOT));
        }
        Matcher matcher = Pattern.compile("[A-Za-z][A-Za-z0-9+#.]{1,}").matcher(questionText);
        while (matcher.find()) {
            terms.add(matcher.group().toLowerCase(Locale.ROOT));
        }
        terms.removeIf(term -> term.length() < 2);
        if (terms.isEmpty()) {
            return 0.0;
        }
        long matched = terms.stream().filter(answerLower::contains).count();
        return matched / (double) terms.size();
    }

    private static int distinctKeywordCount(List<String> values) {
        if (values == null || values.isEmpty()) {
            return 0;
        }
        return (int) values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.strip().toLowerCase(Locale.ROOT))
                .distinct()
                .count();
    }

    private static int jdMatchCount(String text, List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return 0;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return (int) keywords.stream()
                .filter(keyword -> keyword != null && !keyword.isBlank())
                .map(keyword -> keyword.strip().toLowerCase(Locale.ROOT))
                .filter(keyword -> SkillDictionary.containsSkill(text, keyword) || lower.contains(keyword))
                .distinct()
                .count();
    }

    private static boolean hasBulletLines(String text) {
        return text.lines().anyMatch(line -> {
            String trimmed = line.strip();
            return trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.matches("^\\d+[.、)]\\s+.*");
        });
    }

    private static boolean endsWithSentencePunctuation(String text) {
        if (text.isEmpty()) {
            return false;
        }
        char last = text.charAt(text.length() - 1);
        return last == '。' || last == '！' || last == '？' || last == '.' || last == '!' || last == '?';
    }

    private static double duplicateSentenceRatio(List<String> sentences) {
        if (sentences.size() < 2) {
            return 0.0;
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String sentence : sentences) {
            unique.add(sentence.toLowerCase(Locale.ROOT));
        }
        return (sentences.size() - unique.size()) / (double) sentences.size();
    }

    private static int countMarkers(String lower) {
        Set<String> hit = new LinkedHashSet<>();
        for (String marker : STRUCTURE_MARKERS) {
            if (lower.contains(marker)) {
                hit.add(marker);
            }
        }
        return hit.size();
    }

    private static int countTradeoffs(String lower) {
        return (int) TRADEOFF_MARKERS.stream().filter(lower::contains).count();
    }

    private static int countFillers(String lower) {
        return (int) FILLERS.stream().filter(lower::contains).count();
    }

    private static List<String> strengths(
            Scenario scenario, Focus focus, Analysis analysis, Map<String, BigDecimal> dimensions) {
        List<String> result = new ArrayList<>();
        if (analysis.referenceCovered() > 0) {
            result.add("覆盖了参考要点中的 " + analysis.referenceCoveredPhrases().getFirst() + " 等关键信息");
        }
        switch (scenario) {
            case PROJECT -> {
                addIf(result, analysis.firstPerson(), "能分清个人职责与团队协作，没有把团队成果笼统算成自己的");
                addIf(result, analysis.numbers() > 0, "用数字说明了项目规模或结果，贡献更容易被验证");
                addIf(result, analysis.goal(), "先交代了业务背景与目标，听者能快速理解项目价值");
                addIf(result, analysis.tradeoffs() > 0, "提到了关键方案的取舍，能看出你参与过决策而不是只执行");
                addIf(result, analysis.reflection(), "有复盘意识，能说出后续改进方向");
                addIf(result, !analysis.skills().isEmpty(), "项目里用到了 " + join(analysis.skills()) + "，技术栈与经历能对上");
                addIf(result, analysis.action(), "能说清自己具体做了什么，不只是描述项目名称");
                addIf(result, dimensions.get("structure").doubleValue() >= 7.0, "讲述顺序清楚，背景、动作和结果没有混在一起");
            }
            case TECHNICAL -> {
                addIf(result, analysis.why(), "不只给结论，还解释了背后的机制或原因");
                addIf(result, analysis.tradeoffs() > 0, "讨论了替代方案与取舍，体现了工程判断");
                addIf(result, analysis.edge(), "覆盖到异常、边界或降级场景，考虑比较完整");
                addIf(result, hasMetric(analysis.text()), "用指标或复杂度数据支撑结论，不是空泛的“更快更稳”");
                addIf(result, !analysis.skills().isEmpty(), "点到 " + join(analysis.skills()) + " 等关键技术，方向没有跑偏");
                addIf(result, focus == Focus.TROUBLESHOOT && analysis.failure(), "能把故障现象和定位过程讲出来，排查思路有迹可循");
                addIf(result, focus == Focus.PERFORMANCE && analysis.numbers() > 0, "给出了性能数据或量级，优化效果可比较");
                addIf(result, dimensions.get("structure").doubleValue() >= 7.0, "回答有分层，结论和论证能分开");
            }
            case BEHAVIOR -> {
                addIf(result, analysis.conflict(), "把冲突或分歧讲了出来，而不是只讲一团和气");
                addIf(result, analysis.action(), "能说清自己采取的具体行动，不只是表态");
                addIf(result, analysis.result() || analysis.numbers() > 0, "交代了行动带来的结果或影响");
                addIf(result, analysis.reflection(), "有复盘，能总结出可迁移的经验");
                addIf(result, analysis.firstPerson(), "个人角色清晰，面试官能判断你的实际贡献");
                addIf(result, dimensions.get("structure").doubleValue() >= 7.0, "情境、行动、结果讲得比较完整");
            }
            case FIT -> {
                addIf(result, !analysis.jdMatched().isEmpty(), "直接对应了岗位要求的 " + join(analysis.jdMatched()) + "，匹配点具体");
                addIf(result, analysis.action(), "用实际经历说明胜任度，而不是只表决心");
                addIf(result, analysis.numbers() > 0, "有结果数据支撑，匹配度更有说服力");
                addIf(result, analysis.motivation(), "说明了选择这个岗位或方向的具体原因");
                addIf(result, analysis.plan(), "提到了学习或落地计划，能看到入职后的路径");
                addIf(result, !analysis.skills().isEmpty(), "技能栈与岗位方向一致");
            }
            default -> {}
        }
        addFallbackStrengths(result, scenario);
        return distinctLimit(result, 4);
    }

    private static void addFallbackStrengths(List<String> result, Scenario scenario) {
        if (result.size() >= 2) {
            return;
        }
        switch (scenario) {
            case PROJECT -> {
                result.add("项目经历的整体脉络已经能讲下来，下一步是补上你个人的关键动作与结果指标");
                result.add("能主动给出背景信息，面试官不需要反复追问项目在做什么");
            }
            case TECHNICAL -> {
                result.add("回答给出了基本结论，下一步补原理与失效场景会更有说服力");
                result.add("围绕题目给出了自己的理解，没有明显跑题");
            }
            case BEHAVIOR -> {
                result.add("能围绕行为问题给出真实经历，下一步把冲突和行动讲细");
                result.add("态度坦诚，愿意讲自己的做法，而不是只给标准答案");
            }
            case FIT -> {
                result.add("表达了对岗位的基本意愿，下一步用经历证明匹配度");
                result.add("技能方向与岗位大致一致，可以在具体项目上再展开");
            }
            default -> {}
        }
    }

    private static List<String> improvements(
            Scenario scenario, Focus focus, Analysis analysis, Map<String, BigDecimal> dimensions) {
        List<String> result = new ArrayList<>();
        List<String> specificMissing = analysis.referenceMissing().stream()
                .filter(point -> !isGenericReference(point))
                .toList();
        List<String> genericMissing = analysis.referenceMissing().stream()
                .filter(FeedbackScorer::isGenericReference)
                .toList();
        if (!specificMissing.isEmpty()) {
            result.add("参考答案还关注「" + join(specificMissing, "」「") + "」，这部分在你的回答里没有展开");
        }
        switch (scenario) {
            case PROJECT -> {
                addIf(result, !analysis.firstPerson(), "用“我负责/我主导”明确个人边界；团队合作的部分也说清你具体做的那一段");
                addIf(result, analysis.numbers() == 0 || !analysis.result(), "补一个结果指标：性能、成本、交付周期或覆盖规模");
                addIf(result, analysis.tradeoffs() == 0, "讲一个关键方案为什么这么选、当时放弃了什么，项目题最看重这类判断");
                addIf(result, !analysis.failure() && !analysis.reflection(), "补一次失败或返工与复盘，比只讲成功更有说服力");
                addIf(result, !analysis.goal(), "先用一两句话交代背景和目标，再进入做法");
                addIf(result, analysis.sentences().size() < 4, "把回答拆成背景-任务-行动-结果四段，每段一句话");
            }
            case TECHNICAL -> {
                addIf(result, !analysis.why(), "先给结论，再补“为什么”：原理、数据或失效场景");
                addIf(result, analysis.tradeoffs() == 0, "补替代方案与取舍：成本、复杂度、一致性怎么权衡");
                addIf(result, !analysis.edge(), "加一段异常或边界场景：超时、并发、降级、数据不一致时怎么办");
                addIf(result, !hasMetric(analysis.text()) && analysis.numbers() == 0, "给出量级或复杂度，不要停在“更快、更稳”");
                addIf(result, analysis.structureMarkers() < 2, "按“结论-原理-取舍-指标”四段组织，避免一条线讲到底");
                addIf(result, focus == Focus.TROUBLESHOOT, "按“现象-定位-根因-修复-预防”讲完整，尤其要说明如何防止复发");
                addIf(result, focus == Focus.PERFORMANCE, "补压测基线、优化前后对比和瓶颈判断依据");
                addIf(result, focus == Focus.DESIGN, "补充容量、一致性与可用性目标，并解释为什么没选其他方案");
            }
            case BEHAVIOR -> {
                boolean asksConflict = containsAny(analysis.questionLower(), CONFLICT_MARKERS);
                addIf(result, asksConflict && !analysis.conflict(), "把冲突点讲清楚：谁有不同意见、你怎么判断、最后怎么达成一致");
                addIf(result, !analysis.action(), "补你具体做了什么，而不是只说“我们最后完成了”");
                addIf(result, !analysis.reflection(), "加一段复盘：如果重来，你会改哪一步");
                addIf(result, !analysis.result() && analysis.numbers() == 0, "说明行动带来的结果或影响范围");
                addIf(result, analysis.result() && analysis.numbers() == 0, "结果已经有了，可以再补一个量级：影响人数、周期或指标变化");
                addIf(result, !analysis.goal(), "先交代情境（S）和目标（T），再讲行动");
                addIf(
                        result,
                        focus == Focus.REFLECTION && analysis.structureMarkers() < 2,
                        "按时间线讲清发现、定位、处置、恢复，并说明每一步的判断依据");
            }
            case FIT -> {
                addIf(result, analysis.jdMatched().isEmpty(), "对照 JD 挑 1-2 个要求，用具体经历逐条证明，不要只谈意愿");
                addIf(result, !analysis.motivation(), "说明为什么是这个岗位或方向，避免只回答“想学习”");
                addIf(result, !analysis.plan(), "补一段入职后的落地计划（30/60/90 天）");
                addIf(result, analysis.numbers() == 0, "用项目结果证明胜任度，而不是只表决心");
                addIf(result, !analysis.jdMatched().isEmpty(), "把匹配证据按 JD 要求分组：一条要求对应一段经历，面试官更容易核对");
            }
            default -> {}
        }
        if (!genericMissing.isEmpty()) {
            result.add("参考答案还关注「" + join(genericMissing, "」「") + "」，这部分在你的回答里没有展开");
        }
        if (analysis.fillers() > 0) {
            result.add("减少口头填充词，用完整句子分点表达，结尾做一次小结");
        }
        addFallbackImprovements(result, scenario, analysis);
        return distinctLimit(result, 4);
    }

    private static boolean isGenericReference(String point) {
        return point.contains("背景")
                || point.contains("目标")
                || point.contains("职责")
                || point.contains("关键行动")
                || point.contains("量化结果")
                || point.contains("关键取舍");
    }

    private static String projectFallback(Analysis analysis) {
        String question = analysis.questionLower();
        if (question.contains("数据") || question.contains("反馈") || question.contains("指标")) {
            return "补充数据口径与样本质量控制：如何去重、抽样和校验有效性，避免只讲结论";
        }
        if (question.contains("接口")
                || question.contains("输出")
                || question.contains("结构")
                || question.contains("json")) {
            return "补接口契约的版本管理与兼容性策略，说明字段变更如何不破坏调用方";
        }
        if (analysis.firstPerson() && analysis.numbers() > 0 && analysis.tradeoffs() > 0) {
            return "要素已经比较完整，最后补一句“如果重来会怎么改”，把判断力收尾";
        }
        return "把“做了什么”升级成“为什么这样做、结果如何”，让面试官看到判断力";
    }

    private static String behaviorFallback(Analysis analysis) {
        String question = analysis.questionLower();
        if (question.contains("故障") || question.contains("失败") || question.contains("定位") || question.contains("处理")) {
            return "补故障时间线和恢复后的验证结果，让面试官看到完整的处置闭环";
        }
        if (analysis.conflict() && analysis.action() && analysis.result() && analysis.reflection()) {
            return "回答要素比较完整，下一步是压缩铺垫、先给冲突结论，让重点更突出";
        }
        return "用 STAR 把情境、行动、结果和复盘连起来，避免只讲态度";
    }

    private static void addFallbackImprovements(List<String> result, Scenario scenario, Analysis analysis) {
        if (result.size() >= 2) {
            return;
        }
        switch (scenario) {
            case PROJECT -> result.add(projectFallback(analysis));
            case TECHNICAL -> result.add(
                    analysis.why() && analysis.tradeoffs() > 0 && analysis.edge()
                            ? "要素已经比较完整，可以补一个方案不适用或失败场景，说明边界"
                            : "把结论背后的机制、取舍和边界补齐，技术深度题不能只答现象");
            case BEHAVIOR -> result.add(behaviorFallback(analysis));
            case FIT -> result.add(
                    !analysis.jdMatched().isEmpty() && analysis.motivation() && analysis.plan() && analysis.result()
                            ? "匹配点已经具体，可以再补一句你能为团队带来的独特价值，例如解决过的难题类型"
                            : "用一两段可验证的经历对应岗位要求，匹配度才站得住");
            default -> {}
        }
    }

    private static List<FeedbackEvidence> evidence(Scenario scenario, Analysis analysis) {
        List<EvidenceCandidate> candidates = new ArrayList<>();
        for (int index = 0; index < analysis.sentences().size(); index++) {
            String sentence = analysis.sentences().get(index);
            String lowerSentence = sentence.toLowerCase(Locale.ROOT);
            int score = sentenceScore(scenario, lowerSentence, sentence);
            if (score > 0) {
                candidates.add(new EvidenceCandidate(index, sentence, score));
            }
        }
        candidates.sort(Comparator.comparingInt(EvidenceCandidate::score).reversed());
        List<EvidenceCandidate> selected =
                new ArrayList<>(candidates.stream().limit(3).toList());
        selected.sort(Comparator.comparingInt(EvidenceCandidate::index));
        List<FeedbackEvidence> result = new ArrayList<>();
        Set<String> usedReasons = new LinkedHashSet<>();
        for (EvidenceCandidate candidate : selected) {
            String reason = evidenceReason(scenario, candidate.sentence().toLowerCase(Locale.ROOT));
            if (!usedReasons.add(reason)) {
                reason = alternateEvidenceReason(scenario);
                usedReasons.add(reason);
            }
            result.add(new FeedbackEvidence(truncate(candidate.sentence().strip(), 90), reason));
        }
        if (result.isEmpty() && !analysis.sentences().isEmpty()) {
            result.add(new FeedbackEvidence(
                    truncate(analysis.sentences().getFirst().strip(), 90), emptyEvidenceReason(scenario)));
        }
        return result;
    }

    private static int sentenceScore(Scenario scenario, String lower, String sentence) {
        int score = 0;
        boolean number = NUMBER.matcher(sentence).find();
        boolean firstPerson = containsAny(lower, FIRST_PERSON);
        switch (scenario) {
            case PROJECT -> {
                score += firstPerson ? 3 : 0;
                score += number ? 2 : 0;
                score += containsAny(lower, RESULT_MARKERS) ? 2 : 0;
                score += containsAny(lower, TRADEOFF_MARKERS) ? 1 : 0;
                score += containsAny(lower, ACTION_MARKERS) ? 1 : 0;
            }
            case TECHNICAL -> {
                score += containsAny(lower, WHY_MARKERS) ? 3 : 0;
                score += containsAny(lower, TRADEOFF_MARKERS) ? 3 : 0;
                score += containsAny(lower, EDGE_MARKERS) ? 2 : 0;
                score += number ? 2 : 0;
                score += SkillDictionary.findSkills(sentence).isEmpty() ? 0 : 1;
            }
            case BEHAVIOR -> {
                score += containsAny(lower, CONFLICT_MARKERS) ? 3 : 0;
                score += containsAny(lower, ACTION_MARKERS) ? 2 : 0;
                score += containsAny(lower, RESULT_MARKERS) || number ? 2 : 0;
                score += containsAny(lower, REFLECTION_MARKERS) ? 2 : 0;
            }
            case FIT -> {
                score += jdHits(sentence) ? 3 : 0;
                score += containsAny(lower, MOTIVATION_MARKERS) ? 2 : 0;
                score += number ? 1 : 0;
                score += containsAny(lower, ACTION_MARKERS) ? 1 : 0;
            }
            default -> {}
        }
        return score;
    }

    private static boolean jdHits(String sentence) {
        return !SkillDictionary.findSkills(sentence).isEmpty();
    }

    private static String evidenceReason(Scenario scenario, String lower) {
        boolean number = NUMBER.matcher(lower).find();
        boolean firstPerson = containsAny(lower, FIRST_PERSON);
        return switch (scenario) {
            case PROJECT -> {
                if (firstPerson && number && containsAny(lower, RESULT_MARKERS)) {
                    yield "明确了个人职责，并给出可验证的结果";
                }
                if (firstPerson && number) {
                    yield "明确了个人职责，并补充了规模数据";
                }
                if (firstPerson) {
                    yield "清楚交代了你负责的部分，贡献边界不模糊";
                }
                if (number || containsAny(lower, RESULT_MARKERS)) {
                    yield "用结果或规模数据支撑了你的贡献";
                }
                if (containsAny(lower, TRADEOFF_MARKERS)) {
                    yield "提到了方案取舍，这是项目题最容易被追问的点";
                }
                yield "这段可以作为项目背景的起点，建议继续补你的关键动作与结果";
            }
            case TECHNICAL -> {
                if (containsAny(lower, WHY_MARKERS)) {
                    yield "解释了机制或原因，是这道技术题的核心得分点";
                }
                if (containsAny(lower, TRADEOFF_MARKERS)) {
                    yield "体现了替代方案与取舍，能看出工程判断";
                }
                if (containsAny(lower, EDGE_MARKERS)) {
                    yield "覆盖了异常或边界场景，考虑比较完整";
                }
                if (number || hasMetric(lower)) {
                    yield "给出了可比较的指标或量级";
                }
                yield "这段给出了结论，建议继续补原理、取舍与边界";
            }
            case BEHAVIOR -> {
                if (containsAny(lower, CONFLICT_MARKERS)) {
                    yield "把冲突与推动过程讲具体了，能看出你的处理方式";
                }
                if (containsAny(lower, REFLECTION_MARKERS)) {
                    yield "有复盘，能体现可迁移的经验";
                }
                if (containsAny(lower, RESULT_MARKERS) || number) {
                    yield "交代了行动带来的结果或影响";
                }
                if (containsAny(lower, ACTION_MARKERS)) {
                    yield "描述了你采取的具体行动，建议再补情境与结果";
                }
                yield "这段可以作为行为细节的开头，建议补上情境、行动和结果";
            }
            case FIT -> {
                if (jdHits(lower)) {
                    yield "直接对应岗位要求中的技能点，匹配证据具体";
                }
                if (containsAny(lower, MOTIVATION_MARKERS)) {
                    yield "说明了选择岗位的原因，动机不是空话";
                }
                if (number) {
                    yield "用结果证明胜任度";
                }
                yield "这段可以进一步对应到 JD 的具体要求，让匹配度更直观";
            }
        };
    }

    private static String alternateEvidenceReason(Scenario scenario) {
        return switch (scenario) {
            case PROJECT -> "补充了项目规模或职责信息，可以继续展开结果";
            case TECHNICAL -> "补充了实现细节，可以继续补指标与边界";
            case BEHAVIOR -> "补充了行动细节，可以继续补结果与复盘";
            case FIT -> "补充了经历信息，可以进一步对应到具体 JD 要求";
        };
    }

    private static String emptyEvidenceReason(Scenario scenario) {
        return switch (scenario) {
            case PROJECT -> "当前只有概括性描述，建议补一段“我做了什么、结果如何”的原话";
            case TECHNICAL -> "当前只有结论性表述，建议补一段解释原理或取舍的原话";
            case BEHAVIOR -> "当前缺少具体情境与行动细节，建议补一段完整事件描述";
            case FIT -> "当前缺少与岗位要求对应的经历，建议补一段可验证的匹配证据";
        };
    }

    private static String narrative(
            Scenario scenario,
            Focus focus,
            Map<String, BigDecimal> dimensions,
            BigDecimal overall,
            List<String> strengths,
            List<String> improvements,
            List<FeedbackEvidence> evidence,
            boolean proPlan) {
        StringBuilder builder = new StringBuilder();
        builder.append("本题属于")
                .append(scenarioLabel(scenario))
                .append("，")
                .append(focusHint(scenario, focus))
                .append("。\n");
        builder.append("得分 ")
                .append(overall.toPlainString())
                .append(" / 10；五维表现为：内容 ")
                .append(dimensions.get("content").toPlainString())
                .append("，结构 ")
                .append(dimensions.get("structure").toPlainString())
                .append("，深度 ")
                .append(dimensions.get("depth").toPlainString())
                .append("，匹配 ")
                .append(dimensions.get("fit").toPlainString())
                .append("，语言 ")
                .append(dimensions.get("language").toPlainString())
                .append("。\n\n");
        builder.append("优点\n");
        for (int index = 0; index < strengths.size(); index++) {
            builder.append(index + 1).append(". ").append(strengths.get(index)).append('\n');
        }
        builder.append("\n改进\n");
        for (int index = 0; index < improvements.size(); index++) {
            builder.append(index + 1)
                    .append(". ")
                    .append(improvements.get(index))
                    .append('\n');
        }
        if (!evidence.isEmpty()) {
            builder.append("\n证据\n");
            for (FeedbackEvidence item : evidence) {
                builder.append("“")
                        .append(item.quote())
                        .append("”——")
                        .append(item.reason())
                        .append('\n');
            }
        }
        if (proPlan) {
            builder.append("\n建议改写\n").append(rewrite(scenario, ""));
        }
        return builder.toString();
    }

    private static String scenarioLabel(Scenario scenario) {
        return switch (scenario) {
            case PROJECT -> "项目经历题";
            case TECHNICAL -> "技术深度题";
            case BEHAVIOR -> "行为面试题";
            case FIT -> "岗位匹配题";
        };
    }

    private static String focusHint(Scenario scenario, Focus focus) {
        return switch (scenario) {
            case PROJECT -> "重点看个人职责、关键动作与结果，而不是项目本身多宏大";
            case TECHNICAL -> switch (focus) {
                case TROUBLESHOOT -> "重点看现象、定位、根因与预防，排查过程比结论更重要";
                case PERFORMANCE -> "重点看基线、瓶颈判断与优化前后对比";
                case DESIGN -> "重点看目标约束、方案取舍与扩展性";
                case PRINCIPLE -> "重点看底层机制与因果链，而不是背结论";
                default -> "重点看原理、取舍与边界条件";
            };
            case BEHAVIOR -> switch (focus) {
                case COLLABORATION -> "重点看冲突处理、推动方式与最终影响";
                case REFLECTION -> "重点看复盘深度与下一次的具体改变";
                default -> "重点看情境、行动、结果与复盘";
            };
            case FIT -> "重点看动机、能力映射与入职后的落地计划";
        };
    }

    private static String rewrite(Scenario scenario, String text) {
        String seed = text == null || text.isBlank() ? "" : "可保留的开头：" + truncate(text.strip(), 80) + "；";
        return switch (scenario) {
            case PROJECT -> "按 STAR 重写：S 项目背景与目标（1 句）→ T 你的职责与边界（1 句）→ A 关键动作与取舍（2-3 点）→ R 量化结果与复盘（1-2 句）。" + seed;
            case TECHNICAL -> "按“结论-原理-取舍-指标/边界”重写：先一句话给结论，再解释机制，接着对比至少一个替代方案并说明取舍，最后补指标或失效场景。";
            case BEHAVIOR -> "按 STAR + 复盘重写：S/T 情境与目标（1-2 句）→ A 你的具体行动与冲突处理（分点）→ R 结果与影响 → L 如果重来会怎么改。";
            case FIT -> "按“动机-匹配证据-落地计划”重写：先说明为什么选择这个岗位，再用 1-2 段经历对应 JD 要求，最后给出 30/60/90 天计划。";
        };
    }

    private static boolean hasMetric(String value) {
        return METRIC.matcher(value).find();
    }

    private static boolean containsAny(String lower, List<String> markers) {
        for (String marker : markers) {
            if (lower.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private static void addIf(List<String> target, boolean condition, String text) {
        if (condition) {
            target.add(text);
        }
    }

    private static List<String> distinctLimit(List<String> items, int limit) {
        Set<String> unique = new LinkedHashSet<>(items);
        return unique.stream().limit(limit).toList();
    }

    private static String join(List<String> values) {
        return join(values, "、");
    }

    private static String join(List<String> values, String separator) {
        return String.join(separator, values.stream().limit(3).toList());
    }

    private static int count(Matcher matcher) {
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private static List<String> sentences(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String part : text.split("[。！？；\\n]+")) {
            String sentence = part.strip();
            if (!sentence.isEmpty()) {
                result.add(sentence);
            }
        }
        return result;
    }

    private static BigDecimal round(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP);
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(10.0, value));
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }

    private enum Scenario {
        PROJECT,
        TECHNICAL,
        BEHAVIOR,
        FIT
    }

    private enum Focus {
        GENERAL,
        TROUBLESHOOT,
        PERFORMANCE,
        DESIGN,
        PRINCIPLE,
        COLLABORATION,
        REFLECTION
    }

    private record RefCoverage(int total, int covered, List<String> coveredPhrases, List<String> missingPhrases) {}

    private record EvidenceCandidate(int index, String sentence, int score) {}

    private record Analysis(
            String text,
            String lower,
            String questionLower,
            List<String> sentences,
            List<String> skills,
            List<String> jdMatched,
            int numbers,
            int structureMarkers,
            int tradeoffs,
            int fillers,
            boolean firstPerson,
            boolean goal,
            boolean action,
            boolean result,
            boolean failure,
            boolean reflection,
            boolean why,
            boolean edge,
            boolean conflict,
            boolean motivation,
            boolean plan,
            int referenceCovered,
            int referenceTotal,
            List<String> referenceCoveredPhrases,
            List<String> referenceMissing) {}

    public record Input(
            String answerText,
            String questionText,
            List<String> referencePoints,
            List<String> jdKeywords,
            boolean proPlan,
            String dimension) {}

    public record Result(
            BigDecimal overallScore,
            Map<String, BigDecimal> dimensions,
            List<String> strengths,
            List<String> improvements,
            List<FeedbackEvidence> evidence,
            String narrative,
            String rewrittenAnswer) {}
}
