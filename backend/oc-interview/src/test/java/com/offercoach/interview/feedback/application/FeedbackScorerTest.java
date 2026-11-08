package com.offercoach.interview.feedback.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class FeedbackScorerTest {

    @Test
    void projectQuestionGivesOwnershipAndResultFeedback() {
        FeedbackScorer.Result result = FeedbackScorer.score(new FeedbackScorer.Input(
                "我在电商项目中负责订单模块，主导了缓存改造，把下单耗时从 800ms 降到 200ms，QPS 提升 3 倍。" + "团队一共 6 个人，我负责核心链路和压测。",
                "介绍一个你最有成就感的项目。",
                List.of("个人职责", "量化结果", "方案取舍"),
                List.of("Java", "Redis"),
                true,
                "PROJECT"));

        assertThat(String.join(" ", result.strengths())).contains("个人职责");
        assertThat(String.join(" ", result.improvements())).contains("取舍");
        assertThat(result.evidence()).isNotEmpty();
        assertThat(result.evidence().getFirst().reason()).contains("职责");
        assertThat(result.rewrittenAnswer()).contains("STAR");
    }

    @Test
    void technicalQuestionGivesMechanismAndBoundaryFeedback() {
        FeedbackScorer.Result result = FeedbackScorer.score(new FeedbackScorer.Input(
                "缓存一致性我们用延迟双删，因为主从复制存在延迟。对比先删缓存再更新数据库的方案后，我们选择延迟双删，" + "并在超时和并发场景加了重试与降级。",
                "Redis 缓存和数据库如何保证一致性？",
                List.of("延迟双删", "主从延迟", "异常场景"),
                List.of("Redis", "MySQL"),
                false,
                "DEPTH"));

        assertThat(String.join(" ", result.strengths())).contains("机制");
        assertThat(String.join(" ", result.improvements())).doesNotContain("背景-任务");
        assertThat(result.evidence()).isNotEmpty();
        assertThat(String.join(
                        " ",
                        result.evidence().stream().map(item -> item.reason()).toList()))
                .contains("机制");
        assertThat(result.narrative()).contains("技术深度题");
    }

    @Test
    void behaviorQuestionGivesConflictAndReflectionFeedback() {
        FeedbackScorer.Result result = FeedbackScorer.score(new FeedbackScorer.Input(
                "当时和产品在排期上有冲突，我组织了对齐会，列出风险和取舍，说服大家先上线核心流程。" + "最终按时交付，复盘后我们把需求评审提前了一周。",
                "讲一次你和同事意见不一致的经历。",
                List.of("冲突处理", "推动", "复盘"),
                List.of(),
                true,
                "BEHAVIOR"));

        assertThat(String.join(" ", result.strengths())).contains("冲突");
        assertThat(String.join(" ", result.improvements())).doesNotContain("技术取舍");
        assertThat(String.join(
                        " ",
                        result.evidence().stream().map(item -> item.reason()).toList()))
                .containsAnyOf("冲突", "复盘");
        assertThat(result.narrative()).contains("行为面试题");
    }

    @Test
    void fitQuestionGivesMotivationAndPlanFeedback() {
        FeedbackScorer.Result result = FeedbackScorer.score(new FeedbackScorer.Input(
                "我选择这个岗位是因为看重团队的技术方向。我做过三年 Java 后端，主导过支付系统重构，QPS 提升 2 倍。" + "入职后计划 30 天熟悉链路，60 天独立承接需求。",
                "为什么选择我们公司的这个岗位？",
                List.of("动机", "匹配经历", "落地计划"),
                List.of("Java", "支付", "高并发"),
                true,
                "FIT"));

        assertThat(String.join(" ", result.strengths())).containsAnyOf("岗位要求", "选择这个岗位");
        assertThat(String.join(" ", result.improvements())).doesNotContain("补一段入职后的落地计划");
        assertThat(String.join(
                        " ",
                        result.evidence().stream().map(item -> item.reason()).toList()))
                .containsAnyOf("岗位要求", "选择岗位");
        assertThat(result.narrative()).contains("岗位匹配题");
    }

    @Test
    void fourScenariosDoNotShareTheSameTemplate() {
        List<FeedbackScorer.Result> results = List.of(
                score("PROJECT", "我负责订单模块，把耗时从 800ms 降到 200ms。"),
                score("DEPTH", "因为主从复制有延迟，所以采用延迟双删，并对比了替代方案。"),
                score("BEHAVIOR", "我和产品有冲突，组织对齐会推动上线，复盘后调整了流程。"),
                score("FIT", "我看重团队方向，做过支付系统，入职后计划 30 天熟悉链路。"));

        Set<String> firstStrengths =
                results.stream().map(result -> result.strengths().getFirst()).collect(Collectors.toSet());
        Set<String> firstImprovements =
                results.stream().map(result -> result.improvements().getFirst()).collect(Collectors.toSet());

        assertThat(firstStrengths).hasSizeGreaterThanOrEqualTo(3);
        assertThat(firstImprovements).hasSizeGreaterThanOrEqualTo(3);
    }

    private static FeedbackScorer.Result score(String dimension, String answer) {
        return FeedbackScorer.score(new FeedbackScorer.Input(
                answer + "补充一些细节，让回答超过一百六十个字符，便于稳定触发长度加分与完整分析。" + answer, "题目", List.of(), List.of(), true, dimension));
    }
}
