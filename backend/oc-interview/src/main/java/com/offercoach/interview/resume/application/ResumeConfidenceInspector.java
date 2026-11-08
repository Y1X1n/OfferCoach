package com.offercoach.interview.resume.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 从 structured.confidence 与条目完整度推导低置信字段，供前端提示人工确认。
 */
@Component
public class ResumeConfidenceInspector {

    private static final double LOW_CONFIDENCE_THRESHOLD = 0.7;
    private static final int MAX_FIELDS = 20;

    public List<String> lowConfidenceFields(Map<String, Object> structured) {
        if (structured == null || structured.isEmpty()) {
            return List.of();
        }
        List<String> fields = new ArrayList<>();
        Map<String, Object> confidence = asMap(structured.get("confidence"));
        for (Map.Entry<String, Object> entry : confidence.entrySet()) {
            double value = asDouble(entry.getValue());
            if (value > 0 && value < LOW_CONFIDENCE_THRESHOLD) {
                fields.add(entry.getKey());
            }
        }
        List<Map<String, Object>> projects = asMapList(structured.get("projects"));
        for (int i = 0; i < projects.size(); i++) {
            if (asList(projects.get(i).get("metrics")).isEmpty()) {
                fields.add("projects[" + i + "].metrics");
            }
        }
        List<Map<String, Object>> experiences = asMapList(structured.get("experiences"));
        for (int i = 0; i < experiences.size(); i++) {
            if (asList(experiences.get(i).get("bullets")).isEmpty()) {
                fields.add("experiences[" + i + "].bullets");
            }
        }
        List<Map<String, Object>> education = asMapList(structured.get("education"));
        for (int i = 0; i < education.size(); i++) {
            if (education.get(i).get("degree") == null) {
                fields.add("education[" + i + "].degree");
            }
        }
        return fields.stream().distinct().limit(MAX_FIELDS).toList();
    }

    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> casted = (Map<String, Object>) map;
            return casted;
        }
        return Map.of();
    }

    private static List<Map<String, Object>> asMapList(Object value) {
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

    private static List<?> asList(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    private static double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text.strip());
            } catch (NumberFormatException exception) {
                return 0;
            }
        }
        return 0;
    }
}
