package com.offercoach.interview.session.application;

import com.offercoach.interview.jd.domain.JobDescription;
import com.offercoach.interview.qbank.JobFamilyClassifier;
import com.offercoach.interview.resume.domain.Resume;
import com.offercoach.interview.support.JsonCodec;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 出题上下文：只读取简历摘要/结构化字段与 JD 结构化字段，不把原文写入日志或响应。
 */
@Component
public class QuestionContextBuilder {

    private final JsonCodec jsonCodec;

    public QuestionContextBuilder(JsonCodec jsonCodec) {
        this.jsonCodec = jsonCodec;
    }

    public QuestionContext build(Resume resume, JobDescription jd) {
        Map<String, Object> resumeStructured = parse(resume.structuredJson());
        Map<String, Object> jdStructured = parse(jd.structuredJson());
        Set<String> keywords = new LinkedHashSet<>();
        keywords.addAll(strings(jdStructured.get("mustHave")));
        keywords.addAll(strings(jdStructured.get("techStack")));
        keywords.addAll(strings(jdStructured.get("niceToHave")));
        keywords.addAll(strings(jdStructured.get("keywords")));
        List<String> resumeSkills = skillNames(resumeStructured);
        keywords.addAll(resumeSkills);
        List<String> projects = projectNames(resumeStructured);
        String title = jd.title() == null ? stringOrNull(jdStructured.get("title")) : jd.title();
        String jobFamily = JobFamilyClassifier.normalize(stringOrNull(jdStructured.get("jobFamily")));
        if (JobFamilyClassifier.DEFAULT_FAMILY.equals(jobFamily)) {
            String inferenceText =
                    String.join(" ", keywords) + " " + (resume.summary() == null ? "" : resume.summary());
            jobFamily = JobFamilyClassifier.infer(title, inferenceText);
        }
        return new QuestionContext(jobFamily, List.copyOf(keywords), resumeSkills, projects, resume.summary(), title);
    }

    private Map<String, Object> parse(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return jsonCodec.readMap(json);
        } catch (IllegalArgumentException exception) {
            return Map.of();
        }
    }

    private static List<String> skillNames(Map<String, Object> structured) {
        List<String> result = new ArrayList<>();
        for (Object item : list(structured.get("skills"))) {
            if (item instanceof Map<?, ?> map) {
                String name = stringOrNull(map.get("name"));
                if (name != null) {
                    result.add(name);
                }
            } else if (item instanceof String text && !text.isBlank()) {
                result.add(text.strip());
            }
        }
        return result;
    }

    private static List<String> projectNames(Map<String, Object> structured) {
        List<String> result = new ArrayList<>();
        for (Object item : list(structured.get("projects"))) {
            if (item instanceof Map<?, ?> map) {
                String name = stringOrNull(map.get("name"));
                if (name != null) {
                    result.add(name);
                }
            }
        }
        return result;
    }

    private static List<?> list(Object value) {
        return value instanceof List<?> items ? items : List.of();
    }

    private static List<String> strings(Object value) {
        List<String> result = new ArrayList<>();
        for (Object item : list(value)) {
            String text = stringOrNull(item);
            if (text != null) {
                result.add(text);
            }
        }
        return result;
    }

    private static String stringOrNull(Object value) {
        return value instanceof String text && !text.isBlank() ? text.strip() : null;
    }

    public record QuestionContext(
            String jobFamily,
            List<String> keywords,
            List<String> skills,
            List<String> projects,
            String resumeSummary,
            String jdTitle) {}
}
