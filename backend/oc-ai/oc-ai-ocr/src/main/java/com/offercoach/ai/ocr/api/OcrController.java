package com.offercoach.ai.ocr.api;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 内部 OCR 端点：文档 / 图片 → 逐页文本；坐标标记原样保留，由调用方决定是否清理。
 */
@RestController
@RequestMapping("/internal/v1/ocr")
public class OcrController {

    private static final Logger log = LoggerFactory.getLogger(OcrController.class);
    private static final String SCENE = "OCR";
    private static final String MODEL = "tools-ocr";
    private static final long MAX_FILE_BYTES = 20L * 1024 * 1024;
    private static final String PURPOSE_TEXT = "text_recognition";
    private static final String PURPOSE_LAYOUT = "rich_layout_recognition";
    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of("pdf", "doc", "docx", "ppt", "pptx", "png", "jpg", "jpeg", "webp", "gif", "bmp", "tif", "tiff");

    private final DeepSeekToolsClient client;
    private final InternalAuth internalAuth;
    private final ToolCallLogger callLogger;
    private final ObjectMapper objectMapper;
    private final AiRuntimeConfigService runtimeConfig;
    private final ConcurrencyLimiter limiter;

    public OcrController(
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
        this.limiter = new ConcurrencyLimiter("ocr", properties.getOcrMaxConcurrency());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ToolDtos.OcrResult recognize(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "purpose", required = false) String purpose,
            HttpServletRequest request) {
        long userId = internalAuth.requireUser(request);
        if (!runtimeConfig.tools().ocrEnabled()) {
            throw new ToolException(ToolErrorKind.NOT_CONFIGURED, "文字识别已被管理员关闭");
        }
        if (file == null || file.isEmpty()) {
            throw new ToolException(ToolErrorKind.INVALID_INPUT, "文件为空");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new ToolException(ToolErrorKind.TOO_LARGE, "文件超过 20 MiB");
        }
        String fileName = file.getOriginalFilename() == null ? "document.pdf" : file.getOriginalFilename();
        String extension = extensionOf(fileName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new ToolException(ToolErrorKind.UNSUPPORTED_FORMAT, "不支持的文件类型：" + extension);
        }
        String normalizedPurpose = PURPOSE_LAYOUT.equalsIgnoreCase(purpose) ? PURPOSE_LAYOUT : PURPOSE_TEXT;
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException exception) {
            throw new ToolException(ToolErrorKind.INVALID_INPUT, "文件读取失败", exception);
        }
        String contentType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();

        long startedAt = System.nanoTime();
        try (ConcurrencyLimiter.Permit ignored = limiter.acquire()) {
            String json = client.ocr(content, fileName, contentType, normalizedPurpose);
            ToolDtos.OcrResult result = parse(json);
            long latencyMs = (System.nanoTime() - startedAt) / 1_000_000;
            callLogger.success(userId, SCENE, MODEL, latencyMs, null, null);
            return result;
        } catch (ToolException exception) {
            long latencyMs = (System.nanoTime() - startedAt) / 1_000_000;
            callLogger.failed(userId, SCENE, MODEL, latencyMs, exception.kind().name());
            log.warn("OCR 识别失败：kind={} reason={}", exception.kind(), exception.getMessage());
            throw exception;
        }
    }

    private ToolDtos.OcrResult parse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json.trim());
            if (root.has("error")) {
                throw new ToolException(
                        ToolErrorKind.UPSTREAM_FAILED,
                        root.path("error").path("message").asText("识别失败"));
            }
            List<ToolDtos.OcrPage> pages = new ArrayList<>();
            for (JsonNode page : root.path("pages")) {
                pages.add(new ToolDtos.OcrPage(
                        page.path("index").asInt(pages.size()),
                        page.path("text").asText(""),
                        page.path("finish_reason").asText("stop")));
            }
            return new ToolDtos.OcrResult(
                    root.path("id").asText(""), root.path("page_count").asInt(pages.size()), pages);
        } catch (IOException exception) {
            throw new ToolException(ToolErrorKind.UPSTREAM_FAILED, "识别结果解析失败", exception);
        }
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
