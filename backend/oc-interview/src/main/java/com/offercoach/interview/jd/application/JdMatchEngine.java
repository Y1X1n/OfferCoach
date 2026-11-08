package com.offercoach.interview.jd.application;

import com.offercoach.interview.jd.domain.MatchResult;
import com.offercoach.interview.jd.domain.SkillDictionary;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 确定性匹配：技能词典命中 + 原文证据 + 加权分，AI 不可用时兜底。
 */
@Component
public class JdMatchEngine {

    private static final Pattern YEARS_REQUIRED = Pattern.compile("(\\d{1,2})\\s*年(?:以上)?");

    public MatchResult match(
            Map<String, Object> jdStructured,
            String jdRawText,
            Map<String, Object> resumeStructured,
            String resumeRawText) {
        List<String> must = canonicalList(jdStructured.get("mustHave"));
        List<String> nice = canonicalList(jdStructured.get("niceToHave"));
        List<String> tech = canonicalList(jdStructured.get("techStack"));
        if (must.isEmpty() && nice.isEmpty() && tech.isEmpty()) {
            tech = SkillDictionary.findSkills(jdRawText);
        }

        String resumeCorpus = resumeCorpus(resumeStructured, resumeRawText);
        Set<String> resumeSkills = new LinkedHashSet<>(SkillDictionary.findSkills(resumeCorpus));
        resumeSkills.addAll(canonicalList(resumeStructured.get("skills")));

        Set<String> demand = new LinkedHashSet<>();
        demand.addAll(must);
        demand.addAll(nice);
        demand.addAll(tech);

        Set<String> matched = new LinkedHashSet<>();
        for (String skill : demand) {
            if (isMatched(skill, resumeCorpus, resumeSkills)) {
                matched.add(skill);
            }
        }

        List<MatchResult.MatchedSkill> matchedSkills = new ArrayList<>();
        for (String skill : demand) {
            if (!matched.contains(skill)) {
                continue;
            }
            String evidence = quoteFor(resumeRawText, skill);
            matchedSkills.add(new MatchResult.MatchedSkill(skill, evidence == null ? "技能清单包含 " + skill : evidence));
        }

        List<MatchResult.MissingSkill> missingSkills = new ArrayList<>();
        Set<String> missing = new LinkedHashSet<>();
        for (String skill : must) {
            if (!matched.contains(skill)) {
                missing.add(skill);
            }
        }
        for (String skill : tech) {
            if (!matched.contains(skill)) {
                missing.add(skill);
            }
        }
        for (String skill : missing) {
            missingSkills.add(new MatchResult.MissingSkill(skill, "在项目或简历中补充与「" + skill + "」相关的实践与量化结果"));
        }

        List<String> risks = risks(jdStructured, jdRawText, resumeStructured, resumeCorpus, must, matched);

        List<MatchResult.MatchEvidence> evidence = new ArrayList<>();
        for (String skill : matched) {
            if (evidence.size() >= 8) {
                break;
            }
            String jdQuote = quoteFor(jdRawText, skill);
            String resumeQuote = quoteFor(resumeRawText, skill);
            if (jdQuote != null && resumeQuote != null) {
                evidence.add(new MatchResult.MatchEvidence(jdQuote, resumeQuote));
            }
        }

        double score = score(must, nice, tech, matched, resumeCorpus);
        return new MatchResult(
                score,
                matchedSkills.stream().limit(20).toList(),
                missingSkills.stream().limit(10).toList(),
                risks,
                evidence,
                true);
    }

    private static double score(
            List<String> must, List<String> nice, List<String> tech, Set<String> matched, String resumeCorpus) {
        double totalWeight = 0;
        double matchedWeight = 0;
        for (String skill : must) {
            totalWeight += 3;
            if (matched.contains(skill)) {
                matchedWeight += 3;
            }
        }
        for (String skill : tech) {
            totalWeight += 2;
            if (matched.contains(skill)) {
                matchedWeight += 2;
            }
        }
        for (String skill : nice) {
            totalWeight += 1;
            if (matched.contains(skill)) {
                matchedWeight += 1;
            }
        }
        double base = totalWeight == 0 ? 50.0 : 80.0 * matchedWeight / totalWeight;
        double bonus = resumeCorpus.isBlank() ? 0 : 20.0;
        double score = Math.min(100.0, base + bonus);
        return Math.round(score * 10.0) / 10.0;
    }

    private static List<String> risks(
            Map<String, Object> jdStructured,
            String jdRawText,
            Map<String, Object> resumeStructured,
            String resumeCorpus,
            List<String> must,
            Set<String> matched) {
        List<String> risks = new ArrayList<>();
        List<Map<String, Object>> projects = mapList(resumeStructured.get("projects"));
        if (projects.isEmpty()) {
            risks.add("简历缺少项目经历描述");
        } else {
            boolean hasMetrics = projects.stream()
                    .anyMatch(project -> !listOf(project.get("metrics")).isEmpty());
            if (!hasMetrics) {
                risks.add("项目描述缺少可量化指标");
            }
        }
        int resumeYears = intValue(mapValue(resumeStructured.get("basic"), "yearsOfExperience"));
        int requiredYears = requiredYears(jdRawText);
        if (requiredYears > 0 && resumeYears > 0 && resumeYears < requiredYears) {
            risks.add("工作年限可能不满足 JD 要求（要求 " + requiredYears + " 年，简历约 " + resumeYears + " 年）");
        }
        String seniority = String.valueOf(jdStructured.getOrDefault("seniority", ""));
        if ("SENIOR".equalsIgnoreCase(seniority) && resumeYears > 0 && resumeYears < 3) {
            risks.add("JD 为资深级别，简历工作年限偏少");
        }
        long missingMust =
                must.stream().filter(skill -> !matched.contains(skill)).count();
        if (missingMust >= 3) {
            List<String> samples = must.stream()
                    .filter(skill -> !matched.contains(skill))
                    .limit(3)
                    .toList();
            risks.add("核心要求覆盖不足，缺少 " + String.join("、", samples) + " 等");
        }
        if (resumeCorpus.length() < 200) {
            risks.add("简历内容较少，技能证据不足");
        }
        return risks.stream().distinct().limit(8).toList();
    }

    private static boolean isMatched(String skill, String resumeCorpus, Set<String> resumeSkills) {
        return resumeSkills.contains(skill) || SkillDictionary.containsSkill(resumeCorpus, skill);
    }

    private static String quoteFor(String text, String skill) {
        if (text == null || text.isBlank()) {
            return null;
        }
        for (String sentence : text.split("[。\\n；;！!？?]")) {
            String trimmed = sentence.strip();
            if (trimmed.length() >= 4 && SkillDictionary.containsSkill(trimmed, skill)) {
                return trimmed.length() <= 160 ? trimmed : trimmed.substring(0, 160);
            }
        }
        return null;
    }

    private static String resumeCorpus(Map<String, Object> structured, String rawText) {
        StringBuilder builder = new StringBuilder(rawText == null ? "" : rawText);
        for (Map<String, Object> skill : mapList(structured.get("skills"))) {
            String name = stringValue(skill.get("name"));
            if (name != null) {
                builder.append(' ').append(name);
            }
        }
        for (Map<String, Object> experience : mapList(structured.get("experiences"))) {
            appendIfPresent(builder, experience.get("company"));
            appendIfPresent(builder, experience.get("title"));
            for (Object bullet : listOf(experience.get("bullets"))) {
                appendIfPresent(builder, bullet);
            }
        }
        for (Map<String, Object> project : mapList(structured.get("projects"))) {
            appendIfPresent(builder, project.get("name"));
            appendIfPresent(builder, project.get("role"));
            for (Object item : listOf(project.get("stack"))) {
                appendIfPresent(builder, item);
            }
            for (Object item : listOf(project.get("highlights"))) {
                appendIfPresent(builder, item);
            }
            for (Object item : listOf(project.get("metrics"))) {
                appendIfPresent(builder, item);
            }
        }
        return builder.toString();
    }

    private static void appendIfPresent(StringBuilder builder, Object value) {
        if (value instanceof String text && !text.isBlank()) {
            builder.append(' ').append(text);
        }
    }

    private static int requiredYears(String jdRawText) {
        if (jdRawText == null) {
            return 0;
        }
        Matcher matcher = YEARS_REQUIRED.matcher(jdRawText);
        if (!matcher.find()) {
            return 0;
        }
        try {
            return Math.min(Integer.parseInt(matcher.group(1)), 30);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private static List<String> canonicalList(Object value) {
        Set<String> result = new LinkedHashSet<>();
        for (Object item : listOf(value)) {
            if (item instanceof String text && !text.isBlank()) {
                String canonical = SkillDictionary.canonical(text);
                if (canonical != null) {
                    result.add(canonical);
                }
            } else if (item instanceof Map<?, ?> map && map.get("name") instanceof String name) {
                String canonical = SkillDictionary.canonical(name);
                if (canonical != null) {
                    result.add(canonical);
                }
            }
        }
        return new ArrayList<>(result);
    }

    private static Map<String, Object> mapValue(Object value, String key) {
        if (value instanceof Map<?, ?> map && map.get(key) instanceof Map<?, ?> nested) {
            @SuppressWarnings("unchecked")
            Map<String, Object> casted = (Map<String, Object>) nested;
            return casted;
        }
        if (value instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> casted = (Map<String, Object>) map;
            return casted;
        }
        return Map.of();
    }

    private static List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> casted = (Map<String, Object>) map;
                result.add(casted);
            }
        }
        return result;
    }

    private static List<?> listOf(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    private static String stringValue(Object value) {
        return value instanceof String text && !text.isBlank() ? text.strip() : null;
    }

    private static int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }
}
