package com.offercoach.admin.config;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 成本估算单价：单位是「每百万 token 的微元」，避免浮点误差。
 *
 * <p>仅用于管理端成本看板；未配置的模型回退到默认单价，真实账单仍以供应商为准。</p>
 */
@ConfigurationProperties(prefix = "oc.ai.pricing")
public class AdminAiPricingProperties {

    private long inputMicrosPerMillion = 2_000_000;

    private long outputMicrosPerMillion = 8_000_000;

    private Map<String, ModelPrice> models = Map.of();

    public long getInputMicrosPerMillion() {
        return inputMicrosPerMillion;
    }

    public void setInputMicrosPerMillion(long inputMicrosPerMillion) {
        this.inputMicrosPerMillion = inputMicrosPerMillion;
    }

    public long getOutputMicrosPerMillion() {
        return outputMicrosPerMillion;
    }

    public void setOutputMicrosPerMillion(long outputMicrosPerMillion) {
        this.outputMicrosPerMillion = outputMicrosPerMillion;
    }

    public Map<String, ModelPrice> getModels() {
        return models;
    }

    public void setModels(Map<String, ModelPrice> models) {
        this.models = models == null ? Map.of() : models;
    }

    public ModelPrice priceOf(String model) {
        if (model == null) {
            return new ModelPrice(inputMicrosPerMillion, outputMicrosPerMillion);
        }
        return models.getOrDefault(model, new ModelPrice(inputMicrosPerMillion, outputMicrosPerMillion));
    }

    public record ModelPrice(long inputMicrosPerMillion, long outputMicrosPerMillion) {}
}
