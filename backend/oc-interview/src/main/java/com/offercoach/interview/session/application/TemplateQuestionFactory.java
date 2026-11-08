package com.offercoach.interview.session.application;

import com.offercoach.interview.session.domain.QuestionPlan;
import java.util.List;
import java.util.Map;

/**
 * 模板兜底题目：AI 与题库都不可用时仍能生成结构合规、可评分的题目。
 */
public final class TemplateQuestionFactory {

    private static final Map<String, List<String>> TEMPLATES = Map.of(
            "DEPTH",
                    List.of(
                            "请结合你简历中的项目，深入说明一次关键技术选型的过程：你比较了哪些方案，最终取舍依据是什么？",
                            "请解释你在高并发或性能优化场景下的排查思路，并给出具体的量化结果。",
                            "请说明你对所用技术栈底层原理的理解，并举一个实际应用或踩坑的例子。"),
            "PROJECT",
                    List.of(
                            "请介绍你最近负责的一个项目：目标是什么，你承担了哪些职责，遇到的最大挑战是什么？",
                            "请挑选简历中最有代表性的项目，说明架构设计、关键取舍和最终业务结果。",
                            "请描述一次你主导的项目交付：如何拆解任务、协调资源并保证上线质量？"),
            "BEHAVIOR",
                    List.of(
                            "请描述一次你与同事在技术方案上产生分歧的经历，你如何沟通并推动达成一致？",
                            "请分享一次项目延期或失败的经历：你如何复盘，之后做了哪些改进？",
                            "请举例说明你如何在压力下安排优先级，并保证关键交付按时完成。"),
            "FIT",
                    List.of(
                            "请结合 JD 中的核心要求，说明你匹配的优势，以及入职后需要补足的差距和计划。",
                            "如果让你负责 JD 中描述的核心业务，你会如何在前三个月创造价值？",
                            "请说明你为什么选择这个岗位，以及你的经验如何迁移到该岗位的关键场景。"));

    private static final List<String> DEFAULT_REFERENCE_POINTS = List.of("背景与目标", "个人职责", "关键取舍", "量化结果");
    private static final List<String> PROJECT_FOCUS =
            List.of("说明目标、你的职责、技术取舍与量化结果。", "重点讲清架构设计、关键取舍和最终业务结果。", "说明你在其中的协作与推进过程，以及上线后的复盘。");

    private TemplateQuestionFactory() {}

    public static Draft create(QuestionPlan.Slot slot, int seq, QuestionContextBuilder.QuestionContext context) {
        List<String> options = TEMPLATES.getOrDefault(slot.dimension(), List.of("请介绍一段与岗位相关的经历。"));
        String text = options.get(Math.floorMod(seq - 1, options.size()));
        String skill = context.skills().isEmpty()
                ? null
                : context.skills().get(Math.floorMod(seq - 1, context.skills().size()));
        String project = context.projects().isEmpty()
                ? null
                : context.projects()
                        .get(Math.floorMod(seq - 1, context.projects().size()));
        if ("FIT".equals(slot.dimension()) && skill != null) {
            text = "JD 重点要求「" + skill + "」，请说明你的相关实践、掌握程度与差距，并给出补足计划。";
        } else if ("PROJECT".equals(slot.dimension()) && project != null) {
            text = "请围绕简历项目「" + project + "」，" + PROJECT_FOCUS.get(Math.floorMod(seq - 1, PROJECT_FOCUS.size()));
        }
        String why =
                "该题考察" + dimensionLabel(slot.dimension()) + "，用于校准当前岗位要求的" + dimensionFocus(slot.dimension()) + "。";
        return new Draft(slot.dimension(), slot.difficulty(), text, DEFAULT_REFERENCE_POINTS, why);
    }

    private static String dimensionLabel(String dimension) {
        return switch (dimension) {
            case "DEPTH" -> "专业深度";
            case "PROJECT" -> "项目经验";
            case "BEHAVIOR" -> "行为协作";
            case "FIT" -> "岗位匹配";
            default -> "综合能力";
        };
    }

    private static String dimensionFocus(String dimension) {
        return switch (dimension) {
            case "DEPTH" -> "技术原理与取舍";
            case "PROJECT" -> "端到端交付与结果";
            case "BEHAVIOR" -> "协作与复盘";
            case "FIT" -> "岗位要求覆盖";
            default -> "综合能力";
        };
    }

    /** 模板题目草稿。 */
    public record Draft(
            String dimension, String difficulty, String text, List<String> referencePoints, String whyThisQuestion) {}
}
