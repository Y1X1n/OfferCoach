package com.offercoach.ai.asr.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offercoach.ai.AiRuntimeConfigService;
import com.offercoach.ai.tools.api.ToolDtos;
import com.offercoach.ai.tools.client.DeepSeekToolsClient;
import com.offercoach.ai.tools.concurrent.ConcurrencyLimiter;
import com.offercoach.ai.tools.config.ToolsProperties;
import com.offercoach.ai.tools.error.ToolErrorKind;
import com.offercoach.ai.tools.error.ToolException;
import com.offercoach.ai.tools.internal.InternalAuth;
import com.offercoach.ai.tools.log.ToolCallLogger;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 内部 ASR 端点：接收音频，转写为文本。只做转换，不做会话与额度判断。
 */
@RestController
@RequestMapping("/internal/v1/asr")
public class AsrController {

    private static final Logger log = LoggerFactory.getLogger(AsrController.class);
    private static final String SCENE = "ASR";
    private static final String MODEL = "tools-asr";
    private static final long MAX_AUDIO_BYTES = 8L * 1024 * 1024;

    private final DeepSeekToolsClient client;
    private final InternalAuth internalAuth;
    private final ToolCallLogger callLogger;
    private final ObjectMapper objectMapper;
    private final AiRuntimeConfigService runtimeConfig;
    private final ConcurrencyLimiter limiter;

    public AsrController(
            DeepSeekToolsClient client,
            InternalAuth internalAuth,
            ToolCallLogger callLogger,
            ObjectMapper objectMapper,
            AiRuntimeConfigService runtimeConfig,
            ToolsProperties properties) {
        this.client = client;
        this.internalAuth = internalAuth;
        this.callLogger = callLogger;
        this.objectMapper = objectMapper;
        this.runtimeConfig = runtimeConfig;
        this.limiter = new ConcurrencyLimiter("asr", properties.getAsrMaxConcurrency());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ToolDtos.AsrResult transcribe(@RequestParam("file") MultipartFile file, HttpServletRequest request) {
        long userId = internalAuth.requireUser(request);
        if (!runtimeConfig.tools().asrEnabled()) {
            throw new ToolException(ToolErrorKind.NOT_CONFIGURED, "语音识别已被管理员关闭");
        }
        if (file == null || file.isEmpty()) {
            throw new ToolException(ToolErrorKind.INVALID_INPUT, "音频为空");
        }
        if (file.getSize() > MAX_AUDIO_BYTES) {
            throw new ToolException(ToolErrorKind.TOO_LARGE, "音频超过 8 MiB");
        }
        byte[] audio;
        try {
            audio = file.getBytes();
        } catch (IOException exception) {
            throw new ToolException(ToolErrorKind.INVALID_INPUT, "音频读取失败", exception);
        }
        String fileName = file.getOriginalFilename() == null ? "speech.wav" : file.getOriginalFilename();
        String contentType = file.getContentType() == null ? "audio/wav" : file.getContentType();

        long startedAt = System.nanoTime();
        try (ConcurrencyLimiter.Permit ignored = limiter.acquire()) {
            String json = client.asr(audio, fileName, contentType);
            ToolDtos.AsrResult result = parse(json);
            long latencyMs = (System.nanoTime() - startedAt) / 1_000_000;
            callLogger.success(userId, SCENE, MODEL, latencyMs, null, null);
            return result;
        } catch (ToolException exception) {
            long latencyMs = (System.nanoTime() - startedAt) / 1_000_000;
            callLogger.failed(userId, SCENE, MODEL, latencyMs, exception.kind().name());
            log.warn("ASR 转写失败：kind={} reason={}", exception.kind(), exception.getMessage());
            throw exception;
        }
    }

    private ToolDtos.AsrResult parse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            String text = root.path("text").asText("");
            if (text.isBlank()) {
                throw new ToolException(ToolErrorKind.UPSTREAM_FAILED, "转写结果为空");
            }
            return new ToolDtos.AsrResult(
                    root.path("request_id").asText(""),
                    text.strip(),
                    root.path("duration_ms").isNumber()
                            ? root.path("duration_ms").asInt()
                            : null);
        } catch (IOException exception) {
            throw new ToolException(ToolErrorKind.UPSTREAM_FAILED, "转写结果解析失败", exception);
        }
    }
}
