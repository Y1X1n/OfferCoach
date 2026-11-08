package com.offercoach.interview.jd.application;

import com.offercoach.interview.jd.domain.SkillDictionary;
import com.offercoach.interview.qbank.JobFamilyClassifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * JD 确定性结构化：按章节切分职责/要求/加分项，用技能词典抽取并区分 mustHave 与 niceToHave。
 */
@Component
public class JdHeuristicStructurer {

    private static final Set<String> RESP_HEADINGS =
            Set.of("岗位职责", "工作职责", "职责描述", "工作内容", "你将负责", "你要做什么", "职位描述", "岗位描述");
    private static final Set<String> REQ_HEADINGS =
            Set.of("任职要求", "岗位要求", "任职资格", "我们期待", "技能要求", "职位要求", "你需要", "任职条件");
    private static final Set<String> BONUS_HEADINGS = Set.of("加分项", "加分", "bonus", "优先条件", "加分条件");
    private static final List<String> RESP_VERBS =
            List.of("负责", "参与", "设计", "开发", "维护", "搭建", "优化", "推动", "建设", "落地", "主导");

    public Map<String, Object> structure(String title, String company, String rawText) {
        List<String> lines = splitLines(rawText);
        Buckets buckets = collect(lines);

        List<String> requiredSkills = canonicalSkills(buckets.requirements);
        if (requiredSkills.isEmpty()) {
            requiredSkills = canonicalSkills(lines);
        }
        List<String> must = requiredSkills;
        List<String> niceSkills = canonicalSkills(buckets.bonus);
        List<String> nice =
                niceSkills.stream().filter(skill -> !must.contains(skill)).toList();

        Set<String> techSet = new LinkedHashSet<>(must);
        techSet.addAll(nice);
        if (techSet.isEmpty()) {
            techSet.addAll(SkillDictionary.findSkills(rawText));
        }
        List<String> tech = new ArrayList<>(techSet);

        List<String> keywords = SkillDictionary.findDomainTerms(rawText).stream()
                .filter(term -> !techSet.contains(term))
                .limit(10)
                .toList();

        List<String> responsibilities = responsibilities(buckets, lines);

        Map<String, Object> structured = new LinkedHashMap<>();
        structured.put("title", blankToNull(title));
        structured.put("company", blankToNull(company));
        structured.put("seniority", seniority(rawText));
        structured.put("responsibilities", responsibilities);
        structured.put("mustHave", must);
        structured.put("niceToHave", nice);
        structured.put("techStack", tech);
        structured.put("keywords", keywords);
        structured.put("jobFamily", JobFamilyClassifier.infer(title, rawText));
        structured.put("confidence", must.isEmpty() ? 0.45 : 0.55);
        return structured;
    }

    private Buckets collect(List<String> lines) {
        Buckets buckets = new Buckets();
        List<String> target = buckets.other;
        for (String line : lines) {
            String heading = headingOf(line);
            if (heading != null) {
                target = switch (heading) {
                    case "RESP" -> buckets.responsibilities;
                    case "REQ" -> buckets.requirements;
                    case "BONUS" -> buckets.bonus;
                    default -> buckets.other;
                };
                continue;
            }
            target.add(line);
        }
        return buckets;
    }

    private static String headingOf(String line) {
        String cleaned =
                line.replaceAll("^[#*\\s\\d一二三四五六七八九十、.()（）【】\\[\\]]+", "").replaceAll("[:：\\s]+$", "");
        if (cleaned.length() < 2 || cleaned.length() > 16) {
            return null;
        }
        if (BONUS_HEADINGS.contains(cleaned)) {
            return "BONUS";
        }
        if (REQ_HEADINGS.contains(cleaned)) {
            return "REQ";
        }
        if (RESP_HEADINGS.contains(cleaned)) {
            return "RESP";
        }
        return null;
    }

    private static List<String> responsibilities(Buckets buckets, List<String> allLines) {
        List<String> source = buckets.responsibilities;
        if (source.isEmpty()) {
            source = allLines.stream()
                    .filter(line -> RESP_VERBS.stream().anyMatch(line::contains))
                    .toList();
        }
        List<String> result = new ArrayList<>();
        for (String line : source) {
            String cleaned = stripBullet(line);
            if (cleaned.isEmpty()) {
                continue;
            }
            result.add(truncate(cleaned, 200));
        }
        return result.stream().distinct().limit(12).toList();
    }

    private static List<String> canonicalSkills(List<String> lines) {
        Set<String> result = new LinkedHashSet<>();
        for (String skill : SkillDictionary.findSkills(String.join("\n", lines))) {
            String canonical = SkillDictionary.canonical(skill);
            if (canonical != null) {
                result.add(canonical);
            }
        }
        return new ArrayList<>(result);
    }

    private static String seniority(String rawText) {
        String lower = rawText == null ? "" : rawText.toLowerCase(Locale.ROOT);
        if (lower.contains("实习") || lower.contains("intern")) {
            return "INTERN";
        }
        if (lower.contains("高级")
                || lower.contains("资深")
                || lower.contains("专家")
                || lower.contains("架构师")
                || lower.contains("senior")
                || lower.contains("principal")
                || lower.contains("staff")
                || lower.contains("lead")) {
            return "SENIOR";
        }
        if (lower.contains("初级") || lower.contains("junior") || lower.contains("应届")) {
            return "JUNIOR";
        }
        return "MID";
    }

    private static List<String> splitLines(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        for (String line : rawText.split("\\R")) {
            String stripped = line.strip();
            if (!stripped.isEmpty()) {
                lines.add(stripped);
            }
        }
        return lines;
    }

    private static String stripBullet(String line) {
        return line.strip()
                .replaceAll("^[•·●○▪◦\\-*–—]+\\s*", "")
                .replaceAll("^\\d+[.)、]\\s*", "")
                .strip();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private static final class Buckets {
        private final List<String> responsibilities = new ArrayList<>();
        private final List<String> requirements = new ArrayList<>();
        private final List<String> bonus = new ArrayList<>();
        private final List<String> other = new ArrayList<>();
    }
}
