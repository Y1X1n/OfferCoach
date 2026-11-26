package com.offercoach.app.platform;

import com.offercoach.ai.AiRuntimeConfigService;
import com.offercoach.ai.AiRuntimeConfigService.AiRuntimeSnapshot;
import com.offercoach.ai.AiRuntimeConfigService.ToolSettings;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 平台能力状态：前端据此提示“AI 降级/工具关闭”，避免用户误以为题目质量下降是自身问题。
 *
 * <p>只暴露开关与是否配置密钥，不返回 Base URL、模型或任何密钥片段。</p>
 */
@RestController
@RequestMapping("/api/v1/platform")
public class PlatformStatusController {

    private final AiRuntimeConfigService runtimeConfig;

    public PlatformStatusController(AiRuntimeConfigService runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    @GetMapping("/status")
    public PlatformStatus status() {
        AiRuntimeSnapshot snapshot = runtimeConfig.snapshot();
        ToolSettings tools = snapshot.tools();
        return new PlatformStatus(
                snapshot.enabled(),
                snapshot.hasApiKey(),
                !snapshot.enabled()
                        || !snapshot.hasApiKey()
                        || !tools.asrEnabled()
                        || !tools.ttsEnabled()
                        || !tools.ocrEnabled()
                        || !tools.searchEnabled()
                        || !tools.fetchEnabled(),
                new ToolStatus(
                        tools.asrEnabled(),
                        tools.ttsEnabled(),
                        tools.ocrEnabled(),
                        tools.searchEnabled(),
                        tools.fetchEnabled()));
    }

    public record PlatformStatus(boolean aiEnabled, boolean aiConfigured, boolean degraded, ToolStatus tools) {}

    public record ToolStatus(
            boolean asrEnabled, boolean ttsEnabled, boolean ocrEnabled, boolean searchEnabled, boolean fetchEnabled) {}
}
