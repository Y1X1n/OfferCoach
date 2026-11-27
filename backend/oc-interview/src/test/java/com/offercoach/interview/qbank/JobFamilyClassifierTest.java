package com.offercoach.interview.qbank;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JobFamilyClassifierTest {

    @Test
    void infersFamilyFromTitleFirst() {
        assertThat(JobFamilyClassifier.infer("高级后端工程师", "负责订单系统，熟悉 Redis 与 Kafka"))
                .isEqualTo("后端");
        assertThat(JobFamilyClassifier.infer("AI Agent 工程师", "负责 RAG、MCP 与工具调用"))
                .isEqualTo("AI-Agent");
        assertThat(JobFamilyClassifier.infer("高级前端工程师", "负责 Vue 与浏览器性能优化")).isEqualTo("前端");
        assertThat(JobFamilyClassifier.infer("数据分析师", "负责指标体系与数仓建设")).isEqualTo("数据");
        assertThat(JobFamilyClassifier.infer("测试开发工程师", "负责自动化测试与质量保障")).isEqualTo("测试");
        assertThat(JobFamilyClassifier.infer("算法工程师", "负责推荐算法与数据结构")).isEqualTo("算法");
        assertThat(JobFamilyClassifier.infer("系统架构师", "负责高可用与高并发架构设计")).isEqualTo("系统设计");
    }

    @Test
    void infersIndustryFamiliesForNonTechRoles() {
        assertThat(JobFamilyClassifier.infer("内科病房注册护士", "负责责任制护理与院感防控")).isEqualTo("医疗健康");
        assertThat(JobFamilyClassifier.infer("大客户销售经理", "负责渠道拓展与商务谈判")).isEqualTo("销售");
        assertThat(JobFamilyClassifier.infer("财务主管", "负责总账、税务与成本核算")).isEqualTo("财务");
        assertThat(JobFamilyClassifier.infer("HRBP", "负责招聘、绩效与组织发展")).isEqualTo("人力资源");
        assertThat(JobFamilyClassifier.infer("产品经理", "负责需求分析与 PRD")).isEqualTo("产品");
        assertThat(JobFamilyClassifier.isIndustryFamily("医疗健康")).isTrue();
        assertThat(JobFamilyClassifier.isIndustryFamily("后端")).isFalse();
    }

    @Test
    void fallsBackToBodyWhenTitleHasNoSignal() {
        assertThat(JobFamilyClassifier.infer("工程师", "3 年以上 Java 后端经验，熟悉 Spring 与 MySQL"))
                .isEqualTo("后端");
        assertThat(JobFamilyClassifier.infer("工程师", "负责大模型 Agent 与向量检索")).isEqualTo("AI-Agent");
    }

    @Test
    void normalizesAliasesAndUnknownValues() {
        assertThat(JobFamilyClassifier.normalize("BACKEND")).isEqualTo("后端");
        assertThat(JobFamilyClassifier.normalize("ai")).isEqualTo("AI-Agent");
        assertThat(JobFamilyClassifier.normalize("system_design")).isEqualTo("系统设计");
        assertThat(JobFamilyClassifier.normalize("后端")).isEqualTo("后端");
        assertThat(JobFamilyClassifier.normalize("未知岗位")).isEqualTo("通用");
        assertThat(JobFamilyClassifier.normalize(null)).isEqualTo("通用");
    }
}
