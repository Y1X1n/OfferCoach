package com.offercoach.interview.jd.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.offercoach.interview.jd.domain.MatchResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JdMatchEngineTest {

    private final JdMatchEngine engine = new JdMatchEngine();

    @Test
    void matchesSkillsAndReportsGapsWithEvidence() {
        Map<String, Object> jdStructured = Map.of(
                "mustHave", List.of("Java", "Kubernetes"),
                "niceToHave", List.of("Kafka"),
                "techStack", List.of("Java", "Spring Boot", "Redis"),
                "seniority", "MID");
        Map<String, Object> resumeStructured = Map.of(
                "basic", Map.of("yearsOfExperience", 3),
                "skills", List.of(Map.of("name", "Java"), Map.of("name", "Spring Boot")),
                "projects", List.of(Map.of("name", "订单系统", "metrics", List.of("QPS 提升 30%"))));
        String resumeText = "熟悉 Java 与 Spring Boot，使用 Redis 开发订单系统。";

        MatchResult result = engine.match(jdStructured, "熟悉 Java、Kubernetes 优先。", resumeStructured, resumeText);

        assertThat(result.matchScore()).isBetween(0.0, 100.0);
        assertThat(result.matchedSkills())
                .extracting(MatchResult.MatchedSkill::skill)
                .contains("Java");
        assertThat(result.missingSkills())
                .extracting(MatchResult.MissingSkill::skill)
                .contains("Kubernetes");
        assertThat(result.evidence()).isNotEmpty();
        assertThat(result.degraded()).isTrue();
    }

    @Test
    void returnsNeutralScoreWhenJdHasNoSkills() {
        MatchResult result = engine.match(Map.of(), "负责团队日常管理。", Map.of(), "工作经历若干。");

        assertThat(result.matchScore()).isEqualTo(70.0);
        assertThat(result.matchedSkills()).isEmpty();
        assertThat(result.missingSkills()).isEmpty();
    }
}
