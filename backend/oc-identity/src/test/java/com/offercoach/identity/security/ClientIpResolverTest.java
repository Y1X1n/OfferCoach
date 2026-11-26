package com.offercoach.identity.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.offercoach.identity.config.AuthProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * 客户端 IP 解析：只有「私网直连 + 正确代理密钥」才采信 X-Real-IP。
 */
class ClientIpResolverTest {

    @Test
    void ignoresProxyHeadersWhenSecretNotConfigured() {
        // 未配置 proxy-secret 时无法区分代理写入与客户端伪造，必须 fail-closed 回退直连地址。
        ClientIpResolver resolver = new ClientIpResolver(new AuthProperties());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.18.0.5");
        request.addHeader("X-Real-IP", "203.0.113.7");
        request.addHeader("X-Forwarded-For", "203.0.113.8");

        assertThat(resolver.resolve(request)).isEqualTo("172.18.0.5");
    }

    @Test
    void trustsRealIpWhenSecretMatches() {
        AuthProperties properties = new AuthProperties();
        properties.setProxySecret("s3cret");
        ClientIpResolver resolver = new ClientIpResolver(properties);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.18.0.5");
        request.addHeader(ClientIpResolver.PROXY_SECRET_HEADER, "s3cret");
        request.addHeader("X-Real-IP", "203.0.113.7");
        request.addHeader("X-Forwarded-For", "203.0.113.8");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.7");
    }

    @Test
    void fallsBackToRemoteAddressWhenSecretMismatches() {
        AuthProperties properties = new AuthProperties();
        properties.setProxySecret("s3cret");
        ClientIpResolver resolver = new ClientIpResolver(properties);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.9");
        request.addHeader(ClientIpResolver.PROXY_SECRET_HEADER, "wrong");
        request.addHeader("X-Real-IP", "203.0.113.7");

        assertThat(resolver.resolve(request)).isEqualTo("10.0.0.9");
    }

    @Test
    void ignoresProxyHeadersForPublicRemoteAddress() {
        AuthProperties properties = new AuthProperties();
        properties.setProxySecret("s3cret");
        ClientIpResolver resolver = new ClientIpResolver(properties);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.9");
        request.addHeader(ClientIpResolver.PROXY_SECRET_HEADER, "s3cret");
        request.addHeader("X-Real-IP", "198.51.100.1");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.9");
    }

    @Test
    void rejectsNonIpRealIpEvenWithSecret() {
        AuthProperties properties = new AuthProperties();
        properties.setProxySecret("s3cret");
        ClientIpResolver resolver = new ClientIpResolver(properties);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.18.0.5");
        request.addHeader(ClientIpResolver.PROXY_SECRET_HEADER, "s3cret");
        request.addHeader("X-Real-IP", "not-an-ip");

        assertThat(resolver.resolve(request)).isEqualTo("172.18.0.5");
    }
}
