package com.offercoach.interview.jd.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * JD 去重哈希的归一化：排版差异不应产生新记录，正文差异必须区分。
 */
class JdDedupeHashTest {

    @Test
    void ignoresLineEndingAndTrailingWhitespaceDifferences() {
        String pasted = "岗位职责\n1. 负责订单履约与交易核心链路\n任职要求\n1. 熟悉 Java 与 Spring Cloud";
        String fetched = "岗位职责  \r\n1. 负责订单履约与交易核心链路\t\r\n任职要求  \n1. 熟悉 Java 与 Spring Cloud  ";

        assertThat(JdService.normalizeForHash(fetched)).isEqualTo(JdService.normalizeForHash(pasted));
    }

    @Test
    void collapsesTabsAndFullWidthSpaces() {
        String plain = "任职要求 1. 熟悉 Redis";
        String spaced = "任职要求\u3000\u3000\t1. 熟悉 Redis";

        assertThat(JdService.normalizeForHash(spaced)).isEqualTo(JdService.normalizeForHash(plain));
    }

    @Test
    void keepsDifferentContentDistinct() {
        String a = "任职要求 1. 熟悉 Redis";
        String b = "任职要求 1. 熟悉 Kafka";

        assertThat(JdService.normalizeForHash(a)).isNotEqualTo(JdService.normalizeForHash(b));
    }

    @Test
    void handlesNull() {
        assertThat(JdService.normalizeForHash(null)).isEmpty();
    }
}
