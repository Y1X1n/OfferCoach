package com.offercoach.ai.tools.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.offercoach.ai.tools.config.ToolsProperties;
import com.offercoach.common.trace.TraceContext;
import jakarta.servlet.FilterChain;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TraceMdcFilterTest {

    private final ToolsProperties properties = new ToolsProperties();
    private final TraceMdcFilter filter = new TraceMdcFilter(properties);

    @AfterEach
    void clearMdc() {
        TraceContext.clear();
    }

    @Test
    void copiesUpstreamHeadersIntoMdcAndClearsAfterRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(properties.getRequestIdHeader(), "req_upstream");
        request.addHeader(properties.getUserIdHeader(), "42");
        request.addHeader(properties.getSessionIdHeader(), "7");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> captured = new AtomicReference<>();

        FilterChain chain = (req, res) -> captured.set(
                TraceContext.requestId() + "|" + TraceContext.sessionId() + "|" + MDC.get(TraceContext.USER_ID));
        filter.doFilter(request, response, chain);

        assertThat(captured.get()).isEqualTo("req_upstream|7|42");
        assertThat(response.getHeader(properties.getRequestIdHeader())).isEqualTo("req_upstream");
        assertThat(TraceContext.requestId()).isNull();
        assertThat(TraceContext.sessionId()).isNull();
    }

    @Test
    void generatesRequestIdWhenHeaderMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> captured = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> captured.set(TraceContext.requestId()));

        assertThat(captured.get()).startsWith("req_");
        assertThat(response.getHeader(properties.getRequestIdHeader())).isEqualTo(captured.get());
    }
}
