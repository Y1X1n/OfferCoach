package com.offercoach.interview.resume.application;

import com.offercoach.ai.AiRuntimeConfigService;
import com.offercoach.ai.tools.api.ToolDtos;
import com.offercoach.ai.tools.error.ToolException;
import com.offercoach.ai.tools.gateway.AiToolGateway;
import com.offercoach.common.cache.RedisKeys;
import com.offercoach.common.time.BusinessDate;
import com.offercoach.identity.application.quota.RedisCounterGuard;
import com.offercoach.identity.application.settings.PlatformSettingsService;
import com.offercoach.interview.document.OcrTextCleaner;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 扫描件简历的 OCR 降级：本地抽取文本过短或上传的是图片 / PPT 时调用 OCR 服务。
 *
 * <p>能力开关、每日上限与失败归还都收敛在本类：关闭 / 超限 / 失败时返回 null，
 * 由调用方回退 {@code PARSE_NEED_MANUAL}，不阻断上传。</p>
 */
@Component
public class ResumeOcrFallback {

    private static final Logger log = LoggerFactory.getLogger(ResumeOcrFallback.class);
    private static final Set<String> IMAGE_EXTENSIONS =
            Set.of("png", "jpg", "jpeg", "webp", "gif", "bmp", "tif", "tiff");
    private static final Set<String> SLIDE_EXTENSIONS = Set.of("ppt", "pptx");
    private static final String PURPOSE = "text_recognition";
    private static final Duration DAILY_TTL = Duration.ofDays(1);

    private final AiToolGateway toolGateway;
    private final AiRuntimeConfigService runtimeConfig;
    private final PlatformSettingsService platformSettings;
    private final RedisCounterGuard counterGuard;

    public ResumeOcrFallback(
            AiToolGateway toolGateway,
            AiRuntimeConfigService runtimeConfig,
            PlatformSettingsService platformSettings,
            RedisCounterGuard counterGuard) {
        this.toolGateway = toolGateway;
        this.runtimeConfig = runtimeConfig;
        this.platformSettings = platformSettings;
        this.counterGuard = counterGuard;
    }

    /**
     * 是否需要走 OCR：图片 / PPT 一律识别；PDF / DOCX 由调用方按文本长度判断。
     */
    public boolean requiresOcr(String extension) {
        String ext = extension == null ? "" : extension.toLowerCase(Locale.ROOT);
        return IMAGE_EXTENSIONS.contains(ext) || SLIDE_EXTENSIONS.contains(ext);
    }

    /**
     * 调用 OCR 并返回清理后的纯文本；能力关闭 / 超限 / 失败均返回 null，且不占用配额。
     */
    public String extract(long userId, byte[] content, String fileName, String contentType) {
        if (!runtimeConfig.tools().ocrEnabled()) {
            log.info("OCR 能力已被管理员关闭，跳过识别：userId={}", userId);
            return null;
        }
        int limit = platformSettings.snapshot().ocrDailyLimit();
        String counterKey = RedisKeys.dailyOcr(userId, BusinessDate.todayIso());
        boolean counted = limit > 0;
        if (counted && !counterGuard.tryAcquire(counterKey, limit, DAILY_TTL)) {
            log.warn("简历 OCR 达到每日上限，跳过识别：userId={}", userId);
            return null;
        }
        try {
            ToolDtos.OcrResult result = toolGateway.recognize(userId, content, fileName, contentType, PURPOSE);
            String text = joinPages(result);
            if (text.isEmpty()) {
                if (counted) {
                    counterGuard.release(counterKey);
                }
                return null;
            }
            return text;
        } catch (ToolException exception) {
            if (counted) {
                counterGuard.release(counterKey);
            }
            log.warn("简历 OCR 降级失败：kind={} reason={}", exception.kind(), exception.getMessage());
            return null;
        }
    }

    private static String joinPages(ToolDtos.OcrResult result) {
        StringBuilder builder = new StringBuilder();
        for (ToolDtos.OcrPage page : result.pages()) {
            String cleaned = OcrTextCleaner.clean(page.text());
            if (!cleaned.isEmpty()) {
                builder.append(cleaned).append("\n\n");
            }
        }
        return builder.toString().strip();
    }
}
