package com.offercoach.interview.jd.application;

import com.offercoach.ai.AiProvider;
import com.offercoach.ai.AiScene;
import com.offercoach.interview.ai.AiCallLogger;
import com.offercoach.interview.ai.AiGateway;
import com.offercoach.interview.jd.domain.JobDescription;
import com.offercoach.interview.jd.domain.MatchResult;
import com.offercoach.interview.resume.domain.Resume;
import com.offercoach.interview.support.JsonCodec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * JD 匹配编排：优先 AI 复核，输出非法或不可用时使用确定性文本匹配。
 */
@Service
public class JdMatcher {

    private static final Logger log = LoggerFactory.getLogger(JdMatcher.class);

    private static final int MAX_RESUME_PROMPT_CHARS = 8000;
    private static final int MAX_QUOTE_CHARS = 300;

    private static final String SYSTEM_PROMPT =
            """
            你是招聘匹配分析器。只输出一个 JSON 对象，不要输出解释或 Markdown。
            评分 0-100，保留 1 位小数；匹配项必须引用简历原文证据，缺口给出可执行建议。
            JSON 结构：
            {"matchScore":number,
            "matchedSkills":[{"skill":string,"evidence":string}],
            "missingSkills":[{"skill":string,"suggestion":string}],
            "risks":[string],
            "evidence":[{"jdQuote":string,"resumeQuote":string}]}
            禁止编造简历中不存在的信息；每条 evidence 必须同时来自 JD 与简历原文。
            """;

    private final AiGateway aiGateway;
    private final JsonCodec jsonCodec;
    private final JdMatchEngine matchEngine;
    private final AiCallLogger aiCallLogger;

    public JdMatcher(AiGateway aiGateway, JsonCodec jsonCodec, JdMatchEngine matchEngine, AiCallLogger aiCallLogger) {
        this.aiGateway = aiGateway;
        this.jsonCodec = jsonCodec;
        this.matchEngine = matchEngine;
        this.aiCallLogger = aiCallLogger;
    }

    public MatchResult match(long userId, JobDescription jd, Resume resume) {
        Map<String, Object> jdStructured = parse(jd.structuredJson());
        Map<String, Object> resumeStructured = parse(resume.structuredJson());
        if (aiGateway.available()) {
            MatchResult viaAi = viaAi(userId, jdStructured, jd.rawText(), resume);
            if (viaAi != null) {
                return viaAi;
            }
        } else {
            logAiCall(userId, "DEGRADED", null, System.nanoTime(), "UNAVAILABLE");
        }
        return matchEngine.match(jdStructured, jd.rawText(), resumeStructured, resume.rawText());
    }

    private MatchResult viaAi(long userId, Map<String, Object> jdStructured, String jdRawText, Resume resume) {
        long startedAt = System.nanoTime();
        Optional<AiProvider.AiResult> result;
        try {
            String prompt = "JD 结构化：" + jsonCodec.write(jdStructured)
                    + "\nJD 原文：" + truncate(jdRawText, MAX_RESUME_PROMPT_CHARS)
                    + "\n简历摘要：" + safe(resume.summary())
                    + "\n简历原文：" + truncate(resume.rawText(), MAX_RESUME_PROMPT_CHARS);
            result = aiGateway.complete(
                    AiScene.JD_MATCH, aiGateway.systemPrompt(AiScene.JD_MATCH, SYSTEM_PROMPT), prompt);
        } catch (RuntimeException exception) {
            log.warn("AI JD 匹配调用失败，降级确定性匹配：{}", exception.getClass().getSimpleName());
            logAiCall(userId, "FAILED", null, startedAt, exception.getClass().getSimpleName());
            return null;
        }
        if (result.isEmpty()) {
            logAiCall(userId, "DEGRADED", null, startedAt, "UNAVAILABLE");
            return null;
        }
        AiProvider.AiResult aiResult = result.get();
        if (aiResult.text() == null || aiResult.text().isBlank()) {
            logAiCall(userId, "DEGRADED", aiResult, startedAt, "EMPTY");
            return null;
        }
        MatchResult parsed;
        try {
            parsed = parseAiResult(jsonCodec.readMap(jsonCodec.extractJsonObject(aiResult.text())));
        } catch (RuntimeException exception) {
            log.warn("AI JD 匹配输出非法，降级确定性匹配：{}", exception.getClass().getSimpleName());
            logAiCall(userId, "DEGRADED", aiResult, startedAt, "INVALID_JSON");
            return null;
        }
        if (parsed == null) {
            logAiCall(userId, "DEGRADED", aiResult, startedAt, "INVALID_JSON");
            return null;
        }
        logAiCall(userId, "SUCCESS", aiResult, startedAt, null);
        return parsed;
    }

    private void logAiCall(long userId, String status, AiProvider.AiResult result, long startedAt, String errorCode) {
        Integer tokensIn = result == null ? null : result.tokensIn();
        Integer tokensOut = result == null ? null : result.tokensOut();
        // provider/model 在 ai_call_log 中为 NOT NULL：未真正调用模型时用稳定占位值，保证降级行也能入库。
        String provider = result == null || result.provider() == null ? "unknown" : result.provider();
        String model = result == null || result.model() == null ? "unknown" : result.model();
        aiCallLogger.log(
                userId,
                AiScene.JD_MATCH.name(),
                provider,
                model,
                status,
                tokensIn,
                tokensOut,
                null,
                (System.nanoTime() - startedAt) / 1_000_000,
                errorCode);
    }

    private static MatchResult parseAiResult(Map<String, Object> parsed) {
        if (!(parsed.get("matchScore") instanceof Number scoreValue)) {
            return null;
        }
        List<?> matchedRaw = listOrNull(parsed.get("matchedSkills"));
        List<?> missingRaw = listOrNull(parsed.get("missingSkills"));
        List<?> risksRaw = listOrNull(parsed.get("risks"));
        List<?> evidenceRaw = listOrNull(parsed.get("evidence"));
        if (matchedRaw == null || missingRaw == null || risksRaw == null || evidenceRaw == null) {
            return null;
        }
        List<MatchResult.MatchedSkill> matched = new ArrayList<>();
        for (Object item : matchedRaw) {
            if (item instanceof Map<?, ?> map) {
                String skill = text(map.get("skill"));
                if (skill != null) {
                    matched.add(
                            new MatchResult.MatchedSkill(skill, truncate(text(map.get("evidence")), MAX_QUOTE_CHARS)));
                }
            }
        }
        List<MatchResult.MissingSkill> missing = new ArrayList<>();
        for (Object item : missingRaw) {
            if (item instanceof Map<?, ?> map) {
                String skill = text(map.get("skill"));
                if (skill != null) {
                    missing.add(new MatchResult.MissingSkill(
                            skill, truncate(text(map.get("suggestion")), MAX_QUOTE_CHARS)));
                }
            }
        }
        List<String> risks = new ArrayList<>();
        for (Object item : risksRaw) {
            String risk = text(item);
            if (risk != null) {
                risks.add(truncate(risk, MAX_QUOTE_CHARS));
            }
        }
        List<MatchResult.MatchEvidence> evidence = new ArrayList<>();
        for (Object item : evidenceRaw) {
            if (item instanceof Map<?, ?> map) {
                String jdQuote = text(map.get("jdQuote"));
                String resumeQuote = text(map.get("resumeQuote"));
                if (jdQuote != null && resumeQuote != null) {
                    evidence.add(new MatchResult.MatchEvidence(
                            truncate(jdQuote, MAX_QUOTE_CHARS), truncate(resumeQuote, MAX_QUOTE_CHARS)));
                }
            }
        }
        double score = Math.min(Math.max(scoreValue.doubleValue(), 0), 100);
        score = Math.round(score * 10.0) / 10.0;
        return new MatchResult(score, matched, missing, risks, evidence, false);
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

    private static List<?> listOrNull(Object value) {
        return value instanceof List<?> list ? list : null;
    }

    private static String text(Object value) {
        return value instanceof String text && !text.isBlank() ? text.strip() : null;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
