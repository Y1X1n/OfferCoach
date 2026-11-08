package com.offercoach.interview.ai;

import com.offercoach.ai.AiProvider;
import com.offercoach.ai.AiScene;
import com.offercoach.ai.AiUnavailableException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 业务侧 AI 入口：供应商不可用或调用失败时返回空，由业务走确定性降级。
 */
@Component
public class AiGateway {

    private static final Logger log = LoggerFactory.getLogger(AiGateway.class);

    private final AiProvider provider;
    private final PromptProvider promptProvider;

    public AiGateway(AiProvider provider, PromptProvider promptProvider) {
        this.provider = provider;
        this.promptProvider = promptProvider;
    }

    /**
     * 管理端已发布版本优先，否则使用调用方内置提示词。
     */
    public String systemPrompt(AiScene scene, String fallback) {
        return promptProvider.active(scene).orElse(fallback);
    }

    public boolean available() {
        return provider.available();
    }

    public Optional<AiProvider.AiResult> complete(AiScene scene, String systemPrompt, String userPrompt) {
        if (!provider.available()) {
            return Optional.empty();
        }
        try {
            return Optional.of(provider.complete(scene, systemPrompt, userPrompt));
        } catch (AiUnavailableException ex) {
            log.warn("AI 调用失败，降级处理：scene={} reason={}", scene, ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 流式输出；供应商不可用时返回 false，由调用方自行分块输出降级文本。
     */
    public boolean stream(
            AiScene scene, String systemPrompt, String userPrompt, java.util.function.Consumer<String> onDelta) {
        if (!provider.available()) {
            return false;
        }
        try {
            provider.stream(scene, systemPrompt, userPrompt, onDelta);
            return true;
        } catch (AiUnavailableException ex) {
            log.warn("AI 流式调用失败，降级处理：scene={} reason={}", scene, ex.getMessage());
            return false;
        }
    }
}
