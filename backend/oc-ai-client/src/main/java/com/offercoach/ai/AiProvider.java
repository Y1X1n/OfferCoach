package com.offercoach.ai;

import java.util.function.Consumer;

/**
 * 模型供应商抽象；业务代码只声明场景与提示词。
 */
public interface AiProvider {

    boolean available();

    AiResult complete(AiScene scene, String systemPrompt, String userPrompt);

    /**
     * 流式输出；实现需在首个增量到达时立即回调。
     */
    void stream(AiScene scene, String systemPrompt, String userPrompt, Consumer<String> onDelta);

    record AiResult(String text, String provider, String model, int tokensIn, int tokensOut, long latencyMs) {}
}
