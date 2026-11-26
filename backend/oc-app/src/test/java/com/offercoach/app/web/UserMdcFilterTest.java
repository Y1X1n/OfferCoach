package com.offercoach.app.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.offercoach.common.trace.TraceContext;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class UserMdcFilterTest {

    private final UserMdcFilter filter = new UserMdcFilter();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
        TraceContext.clear();
    }

    @Test
    void putsJwtSubjectIntoMdc() throws Exception {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("42")
                .claim("role", "USER")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        AtomicReference<String> captured = new AtomicReference<>();

        filter.doFilter(
                new MockHttpServletRequest(),
                new MockHttpServletResponse(),
                (request, response) -> captured.set(MDC.get(TraceContext.USER_ID)));

        assertThat(captured.get()).isEqualTo("42");
    }

    @Test
    void leavesMdcUntouchedForAnonymousRequest() throws Exception {
        AtomicReference<String> captured = new AtomicReference<>("unset");

        filter.doFilter(
                new MockHttpServletRequest(),
                new MockHttpServletResponse(),
                (request, response) -> captured.set(MDC.get(TraceContext.USER_ID)));

        assertThat(captured.get()).isNull();
    }
}
