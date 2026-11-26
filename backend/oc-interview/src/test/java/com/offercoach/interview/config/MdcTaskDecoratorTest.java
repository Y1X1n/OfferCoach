package com.offercoach.interview.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.offercoach.common.trace.TraceContext;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class MdcTaskDecoratorTest {

    @AfterEach
    void clear() {
        TraceContext.clear();
    }

    @Test
    void copiesRequestContextIntoWorkerThreadAndRestoresAfterwards() {
        MDC.put(TraceContext.REQUEST_ID, "req_parent");
        AtomicReference<String> captured = new AtomicReference<>();

        Runnable decorated = new MdcTaskDecorator().decorate(() -> captured.set(TraceContext.requestId()));
        decorated.run();

        assertThat(captured.get()).isEqualTo("req_parent");
        assertThat(TraceContext.requestId()).isEqualTo("req_parent");
    }

    @Test
    void clearsWorkerContextWhenParentHasNone() {
        AtomicReference<String> captured = new AtomicReference<>("unset");

        Runnable decorated = new MdcTaskDecorator().decorate(() -> captured.set(TraceContext.requestId()));
        decorated.run();

        assertThat(captured.get()).isNull();
        assertThat(TraceContext.requestId()).isNull();
    }
}
