package com.offercoach.interview.jd.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.offercoach.interview.document.WebContentCleaner;
import org.junit.jupiter.api.Test;

/**
 * JD 正文清理与抽取：导航剥离、"抓不到"判定、截断。
 */
class JdBodyExtractorTest {

    private static final String NAV_ONLY =
            """
            欢迎访问
            首页
            招聘岗位
            招聘公告
            联系我们
            公司介绍
            职位搜索
            工作地点：
            全国
            北京
            上海
            """;

    private static final String REAL_JD =
            """
            首页
            招聘岗位
            联系我们

            高级后端工程师（Java）

            岗位职责
            1. 负责订单履约与交易核心链路的架构设计与研发，保障系统的高可用与可扩展；
            2. 主导高并发场景下的容量评估、缓存一致性与稳定性治理，推动核心指标持续优化；
            3. 参与技术方案评审与代码质量治理，沉淀可复用的中间件与公共组件；
            4. 与产品、测试、运维协作，推动需求高质量交付并复盘线上问题。

            任职要求
            1. 熟练掌握 Java、Spring Boot、Spring Cloud 微服务框架，理解服务治理要点；
            2. 熟悉 MySQL 索引与事务优化，具备分库分表与慢查询治理的实践经验；
            3. 熟悉 Redis 缓存设计与一致性保障，了解 Kafka 消息可靠投递机制；
            4. 具备 Kubernetes 容器化部署与灰度发布经验，熟悉常见监控与告警体系；
            5. 有订单、支付、履约等交易系统经验者优先。

            加分项
            1. 有大型分布式系统的性能调优经验；
            2. 参与过开源项目或有技术分享沉淀。
            """;

    @Test
    void navigationOnlyPageIsNotJd() {
        String cleaned = WebContentCleaner.clean(NAV_ONLY);

        assertThat(JdBodyExtractor.looksLikeJd(cleaned)).isFalse();
    }

    @Test
    void shortPageIsNotJdRegardlessOfKeywords() {
        String cleaned = WebContentCleaner.clean("招聘 JD 职位描述");

        assertThat(JdBodyExtractor.looksLikeJd(cleaned)).isFalse();
    }

    @Test
    void realJdIsRecognizedAndNavigationTrimmed() {
        String cleaned = WebContentCleaner.clean(REAL_JD);

        assertThat(JdBodyExtractor.looksLikeJd(cleaned)).isTrue();

        String body = JdBodyExtractor.extract(cleaned);
        assertThat(body).startsWith("岗位职责");
        assertThat(body).doesNotContain("联系我们");
        assertThat(body).contains("Redis").contains("Kubernetes");
    }

    @Test
    void extractCapsAtLimit() {
        String longText = "岗位职责\n" + "内容".repeat(6000);

        String body = JdBodyExtractor.extract(longText);

        assertThat(body).hasSize(JdBodyExtractor.MAX_LENGTH);
    }

    @Test
    void markdownNoiseIsStripped() {
        String markdown =
                """
                # 高级后端工程师

                薪资：**30-50K**

                - [岗位详情](https://example.com/job/1)
                - `Java` / Spring Cloud

                | 地点 | 经验 |
                |---|---|
                | 上海 | 3 年 |
                """;

        String cleaned = WebContentCleaner.clean(markdown);

        assertThat(cleaned).doesNotContain("#").doesNotContain("**").doesNotContain("`");
        assertThat(cleaned).doesNotContain("https://example.com").doesNotContain("|---");
        assertThat(cleaned).contains("高级后端工程师").contains("30-50K").contains("上海");
    }
}
