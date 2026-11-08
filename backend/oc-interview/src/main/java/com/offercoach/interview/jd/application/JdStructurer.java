package com.offercoach.interview.jd.application;

import com.offercoach.ai.AiProvider;
import com.offercoach.ai.AiScene;
import com.offercoach.interview.ai.AiGateway;
import com.offercoach.interview.support.JsonCodec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * JD 结构化：优先 AI 输出严格 JSON，失败或非法时回退启发式。
 */
@Service
public class JdStructurer {

    private static final Logger log = LoggerFactory.getLogger(JdStructurer.class);

    private static final int MAX_PROMPT_CHARS = 8000;

    private static final String SYSTEM_PROMPT =
            """
            你是招聘 JD 结构化解析器。只输出一个 JSON 对象，不要输出解释或 Markdown。
            字段必须来自原文，禁止编造；加分项不得放进 mustHave。
            JSON 结构：
            {"title":string|null,"company":string|null,"seniority":"INTERN|JUNIOR|MID|SENIOR",
            "responsibilities":[string],"mustHave":[string],"niceToHave":[string],
            "techStack":[string],"keywords":[string],"confidence":number}
            技能名统一写法：Spring Boot、Spring Cloud、Kubernetes（不要写 K8s）、PostgreSQL 等。
            """;

    private final AiGateway aiGateway;
    private final JsonCodec jsonCodec;
    private final JdHeuristicStructurer heuristicStructurer;

    public JdStructurer(AiGateway aiGateway, JsonCodec jsonCodec, JdHeuristicStructurer heuristicStructurer) {
        this.aiGateway = aiGateway;
        this.jsonCodec = jsonCodec;
        this.heuristicStructurer = heuristicStructurer;
    }

    public StructuredJd structure(String title, String company, String rawText) {
        Map<String, Object> structured = aiGateway.available() ? viaAi(title, company, rawText) : null;
        if (structured == null) {
            structured = heuristicStructurer.structure(title, company, rawText);
        }
        if (!(structured.get("title") instanceof String)) {
            structured.put("title", blankToNull(title));
        }
        if (!(structured.get("company") instanceof String)) {
            structured.put("company", blankToNull(company));
        }
        return new StructuredJd(jsonCodec.write(structured), structured);
    }

    public boolean isValid(Map<String, Object> structured) {
        if (structured == null) {
            return false;
        }
        return structured.get("responsibilities") instanceof List
                || structured.get("mustHave") instanceof List
                || structured.get("techStack") instanceof List;
    }

    public void normalize(Map<String, Object> structured) {
        for (String key : List.of("responsibilities", "mustHave", "niceToHave", "techStack", "keywords")) {
            structured.put(key, asStringList(structured.get(key)));
        }
        structured.put("title", asString(structured.get("title")));
        structured.put("company", asString(structured.get("company")));
        String seniority = asString(structured.get("seniority"));
        structured.put("seniority", seniority == null ? "MID" : seniority.toUpperCase());
        double confidence = asDouble(structured.get("confidence"), 0.5);
        structured.put("confidence", Math.round(Math.min(Math.max(confidence, 0.3), 0.95) * 100.0) / 100.0);
    }

    private Map<String, Object> viaAi(String title, String company, String rawText) {
        try {
            String prompt = "岗位标题：" + safe(title) + "\n公司：" + safe(company) + "\nJD 原文：\n"
                    + truncate(rawText, MAX_PROMPT_CHARS);
            Optional<AiProvider.AiResult> result = aiGateway.complete(
                    AiScene.JD_ANALYZE, aiGateway.systemPrompt(AiScene.JD_ANALYZE, SYSTEM_PROMPT), prompt);
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
            log.warn("AI JD 结构化输出非法，降级启发式：{}", exception.getClass().getSimpleName());
            return null;
        }
    }

    private static List<String> asStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof String text && !text.isBlank()) {
                result.add(text.strip());
            } else if (item instanceof Map<?, ?> map && map.get("name") instanceof String name) {
                result.add(name.strip());
            }
        }
        return result.stream().distinct().toList();
    }

    private static String asString(Object value) {
        return value instanceof String text && !text.isBlank() ? text.strip() : null;
    }

    private static double asDouble(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return fallback;
    }

    private static String safe(String value) {
        return value == null ? "" : value.strip();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    /**
     * 结构化 JD 结果；json 直接写入 structured_json。
     */
    public record StructuredJd(String json, Map<String, Object> structured) {}
}
