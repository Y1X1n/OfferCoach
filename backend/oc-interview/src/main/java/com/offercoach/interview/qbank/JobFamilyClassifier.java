package com.offercoach.interview.qbank;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 岗位族推断与归一化：把 JD 标题/正文映射到题库的 8 个岗位族，供出题检索使用。
 */
public final class JobFamilyClassifier {

    public static final String DEFAULT_FAMILY = "通用";

    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("backend", "后端"),
            Map.entry("后端开发", "后端"),
            Map.entry("服务端", "后端"),
            Map.entry("服务端开发", "后端"),
            Map.entry("frontend", "前端"),
            Map.entry("front-end", "前端"),
            Map.entry("ai", "AI-Agent"),
            Map.entry("agent", "AI-Agent"),
            Map.entry("ai-agent", "AI-Agent"),
            Map.entry("ai_agent", "AI-Agent"),
            Map.entry("大模型", "AI-Agent"),
            Map.entry("机器学习", "AI-Agent"),
            Map.entry("数据", "数据"),
            Map.entry("数据分析", "数据"),
            Map.entry("algorithm", "算法"),
            Map.entry("test", "测试"),
            Map.entry("qa", "测试"),
            Map.entry("system-design", "系统设计"),
            Map.entry("system_design", "系统设计"),
            Map.entry("general", DEFAULT_FAMILY));

    private static final List<Rule> RULES = List.of(
            new Rule(
                    "AI-Agent",
                    List.of("大模型", "llm", "agent", "rag", "mcp", "prompt", "提示词", "向量", "推理", "微调", "模型", "ai")),
            new Rule("前端", List.of("前端", "vue", "react", "javascript", "typescript", "css", "浏览器", "小程序", "web")),
            new Rule("测试", List.of("测试", "qa", "自动化测试", "用例", "质量保障")),
            new Rule("数据", List.of("数据分析", "数据开发", "数仓", "数据仓库", "指标体系", "etl", "报表", "数据平台")),
            new Rule("算法", List.of("算法", "数据结构", "leetcode", "机器学习", "深度学习", "推荐", "nlp", "cv")),
            new Rule("系统设计", List.of("系统设计", "架构师", "架构设计", "高可用", "高并发")),
            new Rule("后端", List.of("后端", "服务端", "java", "spring", "go", "mysql", "redis", "kafka", "微服务", "分布式")));

    private JobFamilyClassifier() {}

    /**
     * 归一化已有岗位族；未知值回退“通用”，避免与题库中文岗位族对不上。
     */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_FAMILY;
        }
        String value = raw.strip();
        String mapped = ALIASES.get(value.toLowerCase(Locale.ROOT));
        if (mapped != null) {
            return mapped;
        }
        for (Rule rule : RULES) {
            if (rule.family().equals(value)) {
                return value;
            }
        }
        return DEFAULT_FAMILY;
    }

    /**
     * 先看标题再看正文，命中第一条规则即返回；无命中时回退“通用”。
     */
    public static String infer(String title, String text) {
        String titleText = title == null ? "" : title.toLowerCase(Locale.ROOT);
        String bodyText = (title == null ? "" : title + "\n" + (text == null ? "" : text)).toLowerCase(Locale.ROOT);
        for (Rule rule : RULES) {
            if (containsAny(titleText, rule.keywords())) {
                return rule.family();
            }
        }
        for (Rule rule : RULES) {
            if (containsAny(bodyText, rule.keywords())) {
                return rule.family();
            }
        }
        return DEFAULT_FAMILY;
    }

    private static boolean containsAny(String text, List<String> keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private record Rule(String family, List<String> keywords) {}
}
