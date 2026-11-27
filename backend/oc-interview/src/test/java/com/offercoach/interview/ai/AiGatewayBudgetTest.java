package com.offercoach.interview.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.offercoach.ai.AiProvider;
import com.offercoach.ai.AiScene;
import com.offercoach.ai.AiUnavailableException;
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

    /**
     * Jackson 3 的解析异常是不受检的，供应商适配层必须自己把它包成 AiUnavailableException，
     * 否则会直接冒泡成 500，而不是走这里约定的降级分支。
     */
    @Test
    void providerFailureDegradesInsteadOfPropagating() {
        AiProvider provider = mock(AiProvider.class);
        when(provider.available()).thenReturn(true);
        doThrow(new AiUnavailableException("AI 响应解析失败")).when(provider).complete(any(), any(), any());
        doThrow(new AiUnavailableException("AI 流式响应解析失败")).when(provider).stream(any(), any(), any(), any());
        AiBudgetGuard guard = mock(AiBudgetGuard.class);
        when(guard.exhausted()).thenReturn(false);
        AiGateway gateway = new AiGateway(provider, mock(PromptProvider.class), guard);

        assertThat(gateway.complete(AiScene.QUESTION_GEN, "system", "user")).isEmpty();
        assertThat(gateway.stream(AiScene.QUESTION_GEN, "system", "user", delta -> {}))
                .isFalse();
    }
}
