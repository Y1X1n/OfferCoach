package com.offercoach.interview.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.offercoach.ai.AiProvider;
import com.offercoach.ai.AiScene;
import org.junit.jupiter.api.Test;

/**
 * 日 token 预算熔断必须真正影响业务可用性判断。
 */
class AiGatewayBudgetTest {

    @Test
    void budgetExhaustedMakesGatewayUnavailable() {
        AiProvider provider = mock(AiProvider.class);
        when(provider.available()).thenReturn(true);
        AiBudgetGuard guard = mock(AiBudgetGuard.class);
        when(guard.exhausted()).thenReturn(true);
        AiGateway gateway = new AiGateway(provider, mock(PromptProvider.class), guard);

        assertThat(gateway.available()).isFalse();
        assertThat(gateway.complete(AiScene.QUESTION_GEN, "system", "user")).isEmpty();
        assertThat(gateway.stream(AiScene.QUESTION_GEN, "system", "user", delta -> {}))
                .isFalse();
    }

    @Test
    void budgetWithinLimitKeepsGatewayAvailable() {
        AiProvider provider = mock(AiProvider.class);
        when(provider.available()).thenReturn(true);
        AiBudgetGuard guard = mock(AiBudgetGuard.class);
        when(guard.exhausted()).thenReturn(false);
        AiGateway gateway = new AiGateway(provider, mock(PromptProvider.class), guard);

        assertThat(gateway.available()).isTrue();
    }
}
