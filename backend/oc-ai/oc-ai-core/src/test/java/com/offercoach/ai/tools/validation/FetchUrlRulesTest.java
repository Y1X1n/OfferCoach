package com.offercoach.ai.tools.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * 抓取 URL 校验：协议、凭据、本机/私网/云元数据字面量地址。
 */
class FetchUrlRulesTest {

    @Test
    void acceptsOrdinaryHttpsUrl() {
        assertThat(FetchUrlRules.requireValid(" https://jobs.example.com/a/1?x=1 "))
                .isEqualTo("https://jobs.example.com/a/1?x=1");
    }

    @Test
    void rejectsNonHttpSchemesAndMissingHost() {
        assertThatThrownBy(() -> FetchUrlRules.requireValid("ftp://example.com/a"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FetchUrlRules.requireValid("file:///etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FetchUrlRules.requireValid("https://")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FetchUrlRules.requireValid("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FetchUrlRules.requireValid(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsEmbeddedCredentials() {
        assertThatThrownBy(() -> FetchUrlRules.requireValid("https://user:pass@example.com/a"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsLoopbackPrivateAndMetadataLiterals() {
        for (String host : new String[] {
            "localhost",
            "app.localhost",
            "127.0.0.1",
            "127.0.0.2",
            "0.0.0.0",
            "10.1.2.3",
            "172.16.0.1",
            "172.31.255.255",
            "192.168.1.1",
            "169.254.169.254",
            "100.64.0.1",
            "192.0.2.10",
            "198.18.0.1",
            "224.0.0.1",
            "metadata.google.internal"
        }) {
            assertThatThrownBy(() -> FetchUrlRules.requireValid("https://" + host + "/jd"))
                    .as(host + " 应被拒绝")
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void rejectsIpv6LoopbackLinkLocalAndUniqueLocal() {
        for (String host : new String[] {"[::1]", "[fe80::1]", "[fc00::1]", "[fd12:3456::1]", "[::]"}) {
            assertThatThrownBy(() -> FetchUrlRules.requireValid("https://" + host + "/jd"))
                    .as(host + " 应被拒绝")
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void rejectsIpv4MappedIpv6Loopback() {
        // ::ffff:127.0.0.1 与 ::ffff:7f00:1 指向同一个回环地址，必须一并拦住。
        assertThatThrownBy(() -> FetchUrlRules.requireValid("https://[::ffff:127.0.0.1]/jd"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FetchUrlRules.requireValid("https://[::ffff:7f00:1]/jd"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void allowsPublicIpv4AndOrdinaryHosts() {
        assertThat(FetchUrlRules.requireValid("https://8.8.8.8/jd")).startsWith("https://8.8.8.8");
        assertThat(FetchUrlRules.requireValid("https://jobs.example.com/jd")).startsWith("https://jobs.example.com");
        assertThat(FetchUrlRules.requireValid("https://172.32.0.1/jd")).startsWith("https://172.32.0.1");
    }
}
