package com.offercoach.interview.session.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 会话配置；与请求体 config 字段一一对应，持久化为 interview_session.config JSONB。
 */
public record SessionConfig(
        int questionCount,
        String difficulty,
        String interviewerStyle,
        String answerMode,
        int timeLimitSec,
        String language) {

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("questionCount", questionCount);
        map.put("difficulty", difficulty);
        map.put("interviewerStyle", interviewerStyle);
        map.put("answerMode", answerMode);
        map.put("timeLimitSec", timeLimitSec);
        map.put("language", language);
        return map;
    }

    /**
     * 从已存 JSON 读取；缺失字段按默认值补齐，避免脏数据阻断查询。
     */
    public static SessionConfig fromMap(Map<String, Object> map, int fallbackCount) {
        int count = intValue(map.get("questionCount"), fallbackCount);
        return new SessionConfig(
                count,
                stringValue(map.get("difficulty"), "MIXED"),
                stringValue(map.get("interviewerStyle"), "STANDARD"),
                stringValue(map.get("answerMode"), "TEXT"),
                intValue(map.get("timeLimitSec"), 180),
                stringValue(map.get("language"), "zh-CN"));
    }

    private static int intValue(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static String stringValue(Object value, String fallback) {
        return value instanceof String text && !text.isBlank() ? text : fallback;
    }
}
