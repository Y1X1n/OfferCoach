package com.offercoach.interview.resume.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ResumeHeuristicParserTest {

    private final ResumeHeuristicParser parser = new ResumeHeuristicParser();

    @Test
    void parsesBasicSectionsAndMasksContactInfo() {
        String resumeText =
                """
                张三
                电话：13812345678 邮箱：zhangsan@example.com 上海
                3 年 Java 后端开发经验

                教育背景
                2018.09-2022.06  复旦大学  计算机科学与技术  本科

                工作经历
                2022.07-2024.06  某某科技有限公司  后端工程师
                - 负责订单系统开发，接口 QPS 提升 30%
                - 使用 Java、Spring Boot、MySQL、Redis

                项目经历
                电商交易系统
                - 主导订单链路重构，日均 200 万请求，耗时降低 40%
                - 技术栈：Java、Kafka、MySQL

                专业技能
                熟悉 Java、Spring Boot、MySQL、Redis、Kafka
                了解 Docker、Kubernetes

                证书
                英语六级证书
                """;

        Map<String, Object> structured = parser.parse(resumeText);

        Map<String, Object> basic = castMap(structured.get("basic"));
        assertThat(basic.get("name")).isEqualTo("张三");
        assertThat(basic.get("phoneMasked")).isEqualTo("138****5678");
        assertThat(basic.get("emailMasked")).isEqualTo("z***@example.com");
        assertThat(basic.get("city")).isEqualTo("上海");
        assertThat(basic.get("yearsOfExperience")).isEqualTo(3);

        assertThat(castList(structured.get("education"))).isNotEmpty();
        assertThat(castList(structured.get("experiences"))).isNotEmpty();
        assertThat(castList(structured.get("projects"))).isNotEmpty();
        assertThat(castList(structured.get("skills"))).isNotEmpty();
        assertThat(castStringList(structured.get("certificates"))).contains("英语六级证书");

        Map<String, Object> firstExperience =
                castMap(castList(structured.get("experiences")).get(0));
        assertThat(firstExperience.get("company")).isEqualTo("某某科技有限公司");
        assertThat(castList(firstExperience.get("bullets"))).isNotEmpty();

        Map<String, Object> firstProject =
                castMap(castList(structured.get("projects")).get(0));
        assertThat(firstProject.get("name")).isEqualTo("电商交易系统");
        assertThat(castList(firstProject.get("metrics"))).isNotEmpty();

        List<String> skillNames = castList(structured.get("skills")).stream()
                .map(ResumeHeuristicParserTest::castMap)
                .map(skill -> String.valueOf(skill.get("name")))
                .toList();
        assertThat(skillNames).contains("Java", "Spring Boot", "Kafka");

        double basicConfidence = ((Number) castMap(structured.get("confidence")).get("basic")).doubleValue();
        assertThat(basicConfidence).isBetween(0.4, 0.6);
    }

    @Test
    void summaryIsShortAndContainsNoRawContactInfo() {
        Map<String, Object> structured = parser.parse(
                """
                李四
                手机 13900001111 邮箱 lisi@test.com 杭州
                5 年开发经验

                工作经历
                2019.07-2024.01  某某网络有限公司  高级开发工程师
                - 负责支付系统重构，错误率下降 60%

                技能
                精通 Java、Redis、Kafka
                """);

        String summary = new ResumeSummaryBuilder().build(structured);
        assertThat(summary).isNotBlank();
        assertThat(summary.length()).isLessThanOrEqualTo(300);
        assertThat(summary).doesNotContain("lisi@test.com").doesNotContain("13900001111");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castList(Object value) {
        return (List<Map<String, Object>>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<String> castStringList(Object value) {
        return (List<String>) value;
    }
}
