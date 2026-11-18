package com.offercoach.identity.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.offercoach.pay.config.PayProperties;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * 内部令牌过滤：路径按容器解码后的 servletPath 判定，原始 URI 含编码/路径参数时直接拒绝。
 *
 * <p>核心回归场景：{@code /%69nternal/...} 在容器里会被解码为 {@code /internal/...}，
 * 旧实现按原始 URI 前缀判断会放行，导致无令牌直达内部接口。</p>
 */
class InternalTokenFilterTest {

    private static final String TOKEN = "test-internal-token";
    private static final String INTERNAL_PATH = "/internal/v1/payments/notify";

    private InternalTokenFilter filter;

    @BeforeEach
    void setUp() {
        PayProperties properties = new PayProperties();
        properties.setInternalToken(TOKEN);
        filter = new InternalTokenFilter(properties);
    }

    @Test
    void passesNonInternalPathWithoutToken() throws Exception {
        MockHttpServletRequest request = request("/api/v1/orders", "/api/v1/orders");
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    }

    @Test
    void rejectsInternalPathWithoutToken() throws Exception {
        MockHttpServletRequest request = request(INTERNAL_PATH, INTERNAL_PATH);
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(response.getContentAsString()).contains("UNAUTHENTICATED");
    }

    @Test
    void rejectsInternalPathWithWrongToken() throws Exception {
        MockHttpServletRequest request = request(INTERNAL_PATH, INTERNAL_PATH);
        request.addHeader("X-OC-Internal-Token", "wrong");
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void passesInternalPathWithValidToken() throws Exception {
        MockHttpServletRequest request = request(INTERNAL_PATH, INTERNAL_PATH);
        request.addHeader("X-OC-Internal-Token", TOKEN);
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    }

    @Test
    void rejectsPercentEncodedRawUriOnInternalRoute() throws Exception {
        // 容器解码后 servletPath 为 /internal/...，但原始 URI 仍是编码形态
        MockHttpServletRequest request = request("/internal/v1/payments/notify", "/%69nternal/v1/payments/notify");
        request.addHeader("X-OC-Internal-Token", TOKEN);
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
    }

    @Test
    void rejectsPathParameterOnInternalRoute() throws Exception {
        MockHttpServletRequest request = request("/internal/v1/payments/notify", "/internal;/v1/payments/notify");
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
    }

    @Test
    void treatsEmptyServletPathAsNonInternal() throws Exception {
        MockHttpServletRequest request = request("", "/internal/v1/payments/notify");
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        // 不退回原始 URI 判定：交给安全链的 denyAll / 认证处理
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void failsClosedWhenTokenNotConfigured() throws Exception {
        PayProperties properties = new PayProperties();
        InternalTokenFilter unconfigured = new InternalTokenFilter(properties);
        MockHttpServletRequest request = request(INTERNAL_PATH, INTERNAL_PATH);
        request.addHeader("X-OC-Internal-Token", "");
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();

        unconfigured.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    }

    private static MockHttpServletRequest request(String servletPath, String requestUri) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", requestUri);
        request.setServletPath(servletPath);
        return request;
    }
}
