package com.offercoach.interview.resume.application;

import com.offercoach.ai.AiProvider;
import com.offercoach.ai.AiScene;
import com.offercoach.interview.ai.AiGateway;
import com.offercoach.interview.support.JsonCodec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 简历结构化：优先 AI，输出非法或不可用时切到启发式，并统一生成摘要与摘要哈希。
 */
@Service
public class ResumeStructurer {

    private static final Logger log = LoggerFactory.getLogger(ResumeStructurer.class);

    private static final int MAX_PROMPT_CHARS = 12000;

    private static final String SYSTEM_PROMPT =
            """
            你是严谨的简历结构化解析器。只输出一个 JSON 对象，不要输出解释或 Markdown。
            所有字段必须来自输入原文，禁止编造；缺失字段填 null 或空数组。
            JSON 结构：
            {"basic":{"name":string|null,"phoneMasked":string|null,"emailMasked":string|null,
            "city":string|null,"yearsOfExperience":number},
            "education":[{"school":string,"degree":string|null,"major":string|null,"start":string|null,
            "end":string|null}],
            "experiences":[{"company":string,"title":string|null,"start":string|null,"end":string|null,
            "bullets":[string]}],
            "projects":[{"name":string,"role":string|null,"stack":[string],"highlights":[string],
            "metrics":[string]}],
            "skills":[{"name":string,"level":"BEGINNER|INTERMEDIATE|ADVANCED","years":number|null}],
            "certificates":[string],
            "confidence":{"basic":number,"education":number,"experiences":number,"projects":number,
            "skills":number}}
            电话与邮箱必须脱敏，例如 138****1234、z***@example.com，不得输出明文。
            """;

    private final AiGateway aiGateway;
    private final JsonCodec jsonCodec;
    private final ResumeHeuristicParser heuristicParser;
    private final ResumeSummaryBuilder summaryBuilder;

    public ResumeStructurer(
            AiGateway aiGateway,
            JsonCodec jsonCodec,
            ResumeHeuristicParser heuristicParser,
            ResumeSummaryBuilder summaryBuilder) {
        this.aiGateway = aiGateway;
        this.jsonCodec = jsonCodec;
        this.heuristicParser = heuristicParser;
        this.summaryBuilder = summaryBuilder;
    }

    public StructuredResume structure(String rawText) {
        Map<String, Object> structured = null;
        if (aiGateway.available()) {
            structured = viaAi(rawText);
        }
        if (structured == null) {
            structured = heuristicParser.parse(rawText);
        }
        String summary = summaryBuilder.build(structured);
        return new StructuredResume(jsonCodec.write(structured), structured, summary, summaryBuilder.sha256(summary));
    }

    /**
     * 校验基本结构：basic 必须为对象，且至少存在一个列表型分区。
     */
    public boolean isValid(Map<String, Object> structured) {
        if (structured == null || !(structured.get("basic") instanceof Map)) {
            return false;
        }
        return structured.get("education") instanceof List
                || structured.get("experiences") instanceof List
                || structured.get("projects") instanceof List
                || structured.get("skills") instanceof List;
    }

    /**
     * 清洗待入库结构：basic 只保留白名单字段并强制脱敏，补齐列表与置信度。
     */
    public void normalize(Map<String, Object> structured) {
        Map<String, Object> basic = asMap(structured.get("basic"));
        Map<String, Object> sanitizedBasic = new LinkedHashMap<>();
        sanitizedBasic.put("name", asString(basic.get("name")));
        sanitizedBasic.put("phoneMasked", sanitizePhone(asString(basic.get("phoneMasked"))));
        sanitizedBasic.put("emailMasked", sanitizeEmail(asString(basic.get("emailMasked"))));
        sanitizedBasic.put("city", asString(basic.get("city")));
        sanitizedBasic.put("yearsOfExperience", asInt(basic.get("yearsOfExperience")));
        structured.put("basic", sanitizedBasic);

        structured.put("education", asMapList(structured.get("education")));
        structured.put("experiences", asMapList(structured.get("experiences")));
        structured.put("projects", asMapList(structured.get("projects")));
        structured.put("skills", asMapList(structured.get("skills")));
        structured.put("certificates", asStringList(structured.get("certificates")));

        Map<String, Object> confidence = asMap(structured.get("confidence"));
        if (confidence.isEmpty()) {
            confidence = new LinkedHashMap<>();
            confidence.put("basic", 0.6);
            confidence.put("education", 0.5);
            confidence.put("experiences", 0.5);
            confidence.put("projects", 0.5);
            confidence.put("skills", 0.5);
        }
        structured.put("confidence", confidence);
    }

    private Map<String, Object> viaAi(String rawText) {
        try {
            String prompt = "请解析以下简历文本：\n" + truncate(rawText, MAX_PROMPT_CHARS);
            Optional<AiProvider.AiResult> result = aiGateway.complete(
                    AiScene.RESUME_PARSE, aiGateway.systemPrompt(AiScene.RESUME_PARSE, SYSTEM_PROMPT), prompt);
            if (result.isEmpty()) {
                return null;
            }
            Map<String, Object> parsed =
                    jsonCodec.readMap(jsonCodec.extractJsonObject(result.get().text()));
            if (!isValid(parsed)) {
                return null;
            }
            normalize(parsed);
            return parsed;
        } catch (RuntimeException exception) {
            log.warn("AI 简历解析输出非法，降级启发式：{}", exception.getClass().getSimpleName());
            return null;
        }
    }

    private static String sanitizePhone(String value) {
        if (value == null) {
            return null;
        }
        if (value.contains("*")) {
            return value;
        }
        return ResumeHeuristicParser.maskPhone(value);
    }

    private static String sanitizeEmail(String value) {
        if (value == null) {
            return null;
        }
        if (value.contains("*")) {
            return value;
        }
        return ResumeHeuristicParser.maskEmail(value);
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

    private static List<String> asStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            String text = asString(item);
            if (text != null) {
                result.add(text);
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

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    /**
     * 结构化结果；json 为入库文本，summary 与 summaryHash 由统一摘要器生成。
     */
    public record StructuredResume(String json, Map<String, Object> structured, String summary, String summaryHash) {}
}
