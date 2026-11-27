package com.offercoach.interview.support;

import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * JSON 序列化边界；解析失败抛 IllegalArgumentException，由调用方决定降级。
 */
@Component
public class JsonCodec {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public JsonCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException ex) {
            throw new IllegalArgumentException("JSON 序列化失败", ex);
        }
    }

    public Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JacksonException ex) {
            throw new IllegalArgumentException("JSON 解析失败", ex);
        }
    }

    public <T> T read(String json, TypeReference<T> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JacksonException ex) {
            throw new IllegalArgumentException("JSON 解析失败", ex);
        }
    }

    /**
     * 从模型输出中提取 JSON 对象；容忍 ```json 代码块与前后说明文字。
     *
     * <p>按大括号配对扫描取第一个完整对象：旧实现用 indexOf('{') + lastIndexOf('}')，
     * 当模型先给示例再给答案（输出多个对象）时会跨对象截取，得到非法 JSON 并整体降级。</p>
     */
    public String extractJsonObject(String text) {
        if (text == null) {
            throw new IllegalArgumentException("模型输出为空");
        }
        int start = text.indexOf('{');
        if (start < 0) {
            throw new IllegalArgumentException("模型输出不含 JSON 对象");
        }
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int index = start; index < text.length(); index++) {
            char current = text.charAt(index);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }
            if (current == '"') {
                inString = true;
            } else if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return text.substring(start, index + 1);
                }
            }
        }
        throw new IllegalArgumentException("模型输出 JSON 对象未闭合");
    }
}
