package com.offercoach.interview.jd.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JdHeuristicStructurerTest {

    private final JdHeuristicStructurer structurer = new JdHeuristicStructurer();

    @Test
    void separatesMustHaveFromNiceToHaveAndExtractsResponsibilities() {
        String jdText =
                """
                高级后端工程师
                岗位职责：
                1. 负责交易系统的架构设计与核心开发；
                2. 优化高并发场景下的系统稳定性；
                3. 参与核心链路性能优化。

                任职要求：
                1. 5 年以上 Java 后端开发经验；
                2. 熟悉 Spring Boot、Spring Cloud、MySQL、Redis；
                3. 熟悉高并发、分布式系统设计。

                加分项：
                1. 熟悉 Kafka、Kubernetes 优先；
                2. 有云原生经验优先。
                """;

        Map<String, Object> structured = structurer.structure("高级后端工程师", "某互联网公司", jdText);

        assertThat(structured.get("seniority")).isEqualTo("SENIOR");
        assertThat(strings(structured.get("mustHave"))).contains("Java", "Spring Boot", "MySQL", "Redis", "高并发");
        assertThat(strings(structured.get("niceToHave"))).contains("Kafka", "Kubernetes");
        assertThat(strings(structured.get("mustHave"))).doesNotContain("Kafka");
        assertThat(strings(structured.get("responsibilities"))).hasSizeGreaterThanOrEqualTo(3);
        assertThat(strings(structured.get("techStack"))).contains("Java", "Kafka");
        assertThat(strings(structured.get("keywords"))).contains("稳定性");
        assertThat(((Number) structured.get("confidence")).doubleValue()).isBetween(0.4, 0.6);
    }

    @Test
    void fallsBackWithoutSectionHeadings() {
        String jdText = "我们正在招聘 Java 开发工程师，要求熟悉 Spring Boot、MySQL 与 Redis，具备高并发经验。";

        Map<String, Object> structured = structurer.structure("Java 工程师", null, jdText);

        assertThat(strings(structured.get("mustHave"))).contains("Java", "Spring Boot");
        assertThat(structured.get("niceToHave")).isEqualTo(List.of());
        assertThat(structured.get("company")).isNull();
        assertThat(structured.get("seniority")).isEqualTo("MID");
    }

    @SuppressWarnings("unchecked")
    private static List<String> strings(Object value) {
        return (List<String>) value;
    }
}
