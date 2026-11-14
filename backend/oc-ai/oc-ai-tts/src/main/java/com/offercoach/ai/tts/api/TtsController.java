package com.offercoach.ai.tts.api;

import com.offercoach.ai.AiRuntimeConfigService;
import com.offercoach.ai.tools.api.ToolDtos;
import com.offercoach.ai.tools.concurrent.ConcurrencyLimiter;
import com.offercoach.ai.tools.config.ToolsProperties;
import com.offercoach.ai.tools.error.ToolErrorKind;
import com.offercoach.ai.tools.error.ToolException;
import com.offercoach.ai.tools.internal.InternalAuth;
import com.offercoach.ai.tools.log.ToolCallLogger;
import com.offercoach.ai.tts.application.TtsService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部 TTS 端点：文本 → 音频字节；调用方负责对浏览器透传。
 */
@RestController
@RequestMapping("/internal/v1/tts")
public class TtsController {

    private static final Logger log = LoggerFactory.getLogger(TtsController.class);
    private static final String SCENE = "TTS";
    private static final String MODEL = "tools-tts";

    private final TtsService ttsService;
    private final InternalAuth internalAuth;
    private final ToolCallLogger callLogger;
    private final AiRuntimeConfigService runtimeConfig;
    private final ConcurrencyLimiter limiter;

    public TtsController(
            TtsService ttsService,
            InternalAuth internalAuth,
            ToolCallLogger callLogger,
            AiRuntimeConfigService runtimeConfig,
            ToolsProperties properties) {
        this.ttsService = ttsService;
        this.internalAuth = internalAuth;
        this.callLogger = callLogger;
        this.runtimeConfig = runtimeConfig;
        this.limiter = new ConcurrencyLimiter("tts", properties.getTtsMaxConcurrency());
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> synthesize(@RequestBody ToolDtos.TtsRequest request, HttpServletRequest http) {
        long userId = internalAuth.requireUser(http);
        if (!runtimeConfig.tools().ttsEnabled()) {
            throw new ToolException(ToolErrorKind.NOT_CONFIGURED, "题目朗读已被管理员关闭");
        }
        long startedAt = System.nanoTime();
        try (ConcurrencyLimiter.Permit ignored = limiter.acquire()) {
            TtsService.Synthesized result = ttsService.synthesize(request.text(), request.voiceId(), request.format());
            long latencyMs = (System.nanoTime() - startedAt) / 1_000_000;
            if (result.cached()) {
                callLogger.degraded(userId, SCENE, MODEL, latencyMs, "CACHE_HIT");
            } else {
                callLogger.success(userId, SCENE, MODEL, latencyMs, null, null);
            }
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(result.contentType()))
                    .header("X-OC-Tts-Cache", result.cached() ? "HIT" : "MISS")
                    .body(result.audio());
        } catch (ToolException exception) {
            long latencyMs = (System.nanoTime() - startedAt) / 1_000_000;
            callLogger.failed(userId, SCENE, MODEL, latencyMs, exception.kind().name());
            log.warn("TTS 合成失败：kind={} reason={}", exception.kind(), exception.getMessage());
            throw exception;
        }
    }

    /**
     * 默认音色查询，供 oc-app 与管理端展示。
     */
    @GetMapping("/voice")
    public ResponseEntity<String> defaultVoice(HttpServletRequest http) {
        internalAuth.requireUser(http);
        return ResponseEntity.ok(ttsService.defaultVoice());
    }
}
