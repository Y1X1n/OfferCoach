package com.offercoach.interview.qbank;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 岗位族推断与归一化：把 JD 标题/正文映射到岗位族，供出题检索与行业题库沉淀使用。
 *
 * <p>先识别行业岗位（医疗、教育、销售、财务等），再回落到技术岗位族；
 * 无法归类时回退「通用」，保证非技术岗位也能正常进入出题链路。</p>
 */
public final class JobFamilyClassifier {

    public static final String DEFAULT_FAMILY = "通用";

    /** 非技术行业族：没有对应题库时宁可走通用模板，也不要拿技术题凑数。 */
    private static final Set<String> INDUSTRY_FAMILIES =
            Set.of("医疗健康", "教育培训", "销售", "市场营销", "运营", "财务", "法务", "人力资源", "产品", "设计", "供应链");

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
            new Rule("医疗健康", List.of("护理", "护士", "临床", "医师", "医生", "药剂", "医学", "康复", "医院", "病历")),
            new Rule("教育培训", List.of("教师", "讲师", "教研", "课程", "教务", "班主任", "培训师", "教学")),
            new Rule("销售", List.of("销售", "大客户", "渠道", "客户经理", "商务拓展", "招商", "bd")),
            new Rule("市场营销", List.of("市场营销", "品牌", "投放", "增长", "营销", "广告")),
            new Rule("运营", List.of("用户运营", "内容运营", "活动运营", "社群", "运营")),
            new Rule("财务", List.of("财务", "会计", "审计", "税务", "出纳", "成本核算", "总账")),
            new Rule("法务", List.of("法务", "律师", "合规", "合同审核", "知识产权")),
            new Rule("人力资源", List.of("人力资源", "hrbp", "招聘", "薪酬", "绩效", "组织发展")),
            new Rule("产品", List.of("产品经理", "产品设计", "需求分析", "prd")),
            new Rule("设计", List.of("ui设计", "ux", "视觉设计", "交互设计", "平面设计", "工业设计", "设计师")),
            new Rule("供应链", List.of("供应链", "采购", "物流", "仓储", "生产计划", "品控", "质量工程", "制造")),
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

    /** 是否为非技术行业族。 */
    public static boolean isIndustryFamily(String family) {
        return family != null && INDUSTRY_FAMILIES.contains(family);
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
