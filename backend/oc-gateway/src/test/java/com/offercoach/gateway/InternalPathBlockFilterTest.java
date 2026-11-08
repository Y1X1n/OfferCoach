package com.offercoach.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

class InternalPathBlockFilterTest {

    private final InternalPathBlockFilter filter = new InternalPathBlockFilter(new ObjectMapper());

    @Test
    void blocksInternalPath() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/internal/v1/x"));

        Mono<Void> result = filter.filter(exchange, e -> {
            throw new AssertionError("internal 路径不应继续调用链");
        });
        result.block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exchange.getResponse().getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
    }

    @Test
    void passesApiPathToChain() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/quota"));
        AtomicBoolean called = new AtomicBoolean();

        Mono<Void> result = filter.filter(exchange, e -> {
            called.set(true);
            return Mono.empty();
        });
        result.block();

        assertThat(called).isTrue();
    }
}
