package com.offercoach.interview.resume.application;

import com.offercoach.ai.tools.api.ToolDtos;
import com.offercoach.ai.tools.error.ToolException;
import com.offercoach.ai.tools.gateway.AiToolGateway;
import com.offercoach.interview.document.OcrTextCleaner;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 扫描件简历的 OCR 降级：本地抽取文本过短或上传的是图片 / PPT 时调用 OCR 服务。
 *
 * <p>OCR 不可用或失败时返回空，由调用方回退 {@code PARSE_NEED_MANUAL}，不阻断上传。</p>
 */
@Component
public class ResumeOcrFallback {

    private static final Logger log = LoggerFactory.getLogger(ResumeOcrFallback.class);
    private static final Set<String> IMAGE_EXTENSIONS =
            Set.of("png", "jpg", "jpeg", "webp", "gif", "bmp", "tif", "tiff");
    private static final Set<String> SLIDE_EXTENSIONS = Set.of("ppt", "pptx");
    private static final String PURPOSE = "text_recognition";

    private final AiToolGateway toolGateway;

    public ResumeOcrFallback(AiToolGateway toolGateway) {
        this.toolGateway = toolGateway;
    }

    /**
     * 是否需要走 OCR：图片 / PPT 一律识别；PDF / DOCX 由调用方按文本长度判断。
     */
    public boolean requiresOcr(String extension) {
        String ext = extension == null ? "" : extension.toLowerCase(Locale.ROOT);
        return IMAGE_EXTENSIONS.contains(ext) || SLIDE_EXTENSIONS.contains(ext);
    }

    /**
     * 调用 OCR 并返回清理后的纯文本；失败返回 null。
     */
    public String extract(long userId, byte[] content, String fileName, String contentType) {
        try {
            ToolDtos.OcrResult result = toolGateway.recognize(userId, content, fileName, contentType, PURPOSE);
            StringBuilder builder = new StringBuilder();
            for (ToolDtos.OcrPage page : result.pages()) {
                String cleaned = OcrTextCleaner.clean(page.text());
                if (!cleaned.isEmpty()) {
                    builder.append(cleaned).append("\n\n");
                }
            }
            return builder.toString().strip();
        } catch (ToolException exception) {
            log.warn("简历 OCR 降级失败：kind={} reason={}", exception.kind(), exception.getMessage());
            return null;
        }
    }
}
