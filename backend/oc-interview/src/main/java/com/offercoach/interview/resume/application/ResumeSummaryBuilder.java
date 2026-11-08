package com.offercoach.interview.resume.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 从结构化结果生成 ≤300 字摘要并计算 SHA-256；只使用已抽取字段，不引入新事实。
 */
@Component
public class ResumeSummaryBuilder {

    private static final int MAX_SUMMARY_LENGTH = 300;

    public String build(Map<String, Object> structured) {
        if (structured == null || structured.isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        Map<String, Object> basic = asMap(structured.get("basic"));
        int years = asInt(basic.get("yearsOfExperience"));
        if (years > 0) {
            parts.add(years + " 年工作经验");
        }
        List<Map<String, Object>> experiences = asMapList(structured.get("experiences"));
        if (!experiences.isEmpty()) {
            Map<String, Object> latest = experiences.get(0);
            String company = asString(latest.get("company"));
            String title = asString(latest.get("title"));
            if (company != null) {
                parts.add("最近任职于 " + company + (title == null ? "" : "（" + title + "）"));
            }
        }
        List<Map<String, Object>> projects = asMapList(structured.get("projects"));
        if (!projects.isEmpty()) {
            List<String> names = projects.stream()
                    .map(project -> asString(project.get("name")))
                    .filter(name -> name != null)
                    .limit(2)
                    .toList();
            if (!names.isEmpty()) {
                parts.add("代表项目 " + String.join("、", names));
            }
        }
        List<Map<String, Object>> skills = asMapList(structured.get("skills"));
        if (!skills.isEmpty()) {
            List<String> names = skills.stream()
                    .map(skill -> asString(skill.get("name")))
                    .filter(name -> name != null)
                    .limit(6)
                    .toList();
            if (!names.isEmpty()) {
                parts.add("主栈 " + String.join("、", names));
            }
        }
        List<Map<String, Object>> education = asMapList(structured.get("education"));
        if (!education.isEmpty()) {
            Map<String, Object> first = education.get(0);
            String degree = asString(first.get("degree"));
            String school = asString(first.get("school"));
            if (degree != null || school != null) {
                StringBuilder builder = new StringBuilder("学历 ");
                if (degree != null) {
                    builder.append(degree);
                }
                if (school != null) {
                    builder.append("（").append(school).append("）");
                }
                parts.add(builder.toString().strip());
            }
        }
        if (parts.isEmpty()) {
            String city = asString(basic.get("city"));
            parts.add(city == null ? "简历已解析并结构化" : city + "求职者简历");
        }
        String summary = String.join("；", parts);
        return summary.length() <= MAX_SUMMARY_LENGTH ? summary : summary.substring(0, MAX_SUMMARY_LENGTH);
    }

    public String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
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

    private static String asString(Object value) {
        if (value instanceof String text && !text.isBlank()) {
            return text.strip();
        }
        return null;
    }

    private static int asInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return (int) Double.parseDouble(text.strip());
            } catch (NumberFormatException exception) {
                return 0;
            }
        }
        return 0;
    }
}
