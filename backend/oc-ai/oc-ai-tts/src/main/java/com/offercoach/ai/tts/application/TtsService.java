package com.offercoach.ai.tts.application;

import com.offercoach.ai.tools.client.DeepSeekToolsClient;
import com.offercoach.ai.tools.config.ToolsProperties;
import com.offercoach.ai.tools.error.ToolErrorKind;
import com.offercoach.ai.tools.error.ToolException;
import com.offercoach.common.digest.Sha256;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * TTS 合成与内容哈希缓存：同一文本 + 音色 + 格式只向上游请求一次。
 *
 * <p>缓存写本地卷，重启不丢；命中不回源也不计费。多副本时各副本独立缓存，
 * 只影响命中率，不影响正确性。</p>
 */
@Service
public class TtsService {

    private static final Logger log = LoggerFactory.getLogger(TtsService.class);
    private static final int MAX_TEXT_CHARS = 10_000;
    private static final int MAX_BYTES = 64 * 1024;

    private final DeepSeekToolsClient client;
    private final Path cacheRoot;
    private final int cacheDays;
    private final String defaultVoice;

    public TtsService(DeepSeekToolsClient client, ToolsProperties properties) {
        this.client = client;
        String dir = properties.getTtsCacheDir();
        this.cacheRoot = dir == null || dir.isBlank() ? null : Paths.get(dir).toAbsolutePath().normalize();
        this.cacheDays = Math.max(0, properties.getTtsCacheDays());
        this.defaultVoice = properties.getTtsDefaultVoice();
    }

    /**
     * 合成语音；命中缓存直接返回字节，否则回源并落缓存。
     */
    public Synthesized synthesize(String text, String voiceId, String format) {
        String normalizedText = text == null ? "" : text.strip();
        if (normalizedText.isEmpty()) {
            throw new ToolException(ToolErrorKind.INVALID_INPUT, "朗读文本不能为空");
        }
        if (normalizedText.length() > MAX_TEXT_CHARS) {
            throw new ToolException(ToolErrorKind.INVALID_INPUT, "朗读文本超过 10000 字符");
        }
        String voice = normalizeVoice(voiceId);
        String audioFormat = "pcm".equalsIgnoreCase(format) ? "pcm" : "wav";
        String hash = Sha256.hex(normalizedText + '\u0000' + voice + '\u0000' + audioFormat);
        Path cached = cachedPath(hash, audioFormat);
        if (cached != null) {
            byte[] bytes = readCache(cached);
            if (bytes != null) {
                return new Synthesized(bytes, contentTypeOf(audioFormat), true);
            }
        }
        if (normalizedText.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new ToolException(ToolErrorKind.INVALID_INPUT, "朗读文本请求体超过 64 KiB");
        }
        try (DeepSeekToolsClient.AudioStream stream = client.tts(normalizedText, voice, audioFormat)) {
            byte[] bytes = stream.body().readAllBytes();
            if (bytes.length == 0) {
                throw new ToolException(ToolErrorKind.UPSTREAM_FAILED, "合成音频为空");
            }
            writeCache(cached, bytes);
            return new Synthesized(bytes, contentTypeOf(audioFormat), false);
        } catch (IOException exception) {
            throw new ToolException(ToolErrorKind.UPSTREAM_FAILED, "合成音频读取失败", exception);
        }
    }

    /**
     * 清理过期音频缓存；命中路径的惰性删除只覆盖"再次被读"的文件，本任务兜底冷数据。
     */
    @Scheduled(cron = "${oc.ai.tools.tts-cache-cron:0 45 3 * * *}")
    public void purgeExpiredCache() {
        if (cacheRoot == null || cacheDays <= 0) {
            return;
        }
        if (!Files.isDirectory(cacheRoot)) {
            return;
        }
        Instant cutoff = Instant.now().minus(cacheDays, ChronoUnit.DAYS);
        int removed = 0;
        try (var stream = Files.walk(cacheRoot)) {
            for (Path path : stream.filter(Files::isRegularFile).toList()) {
                try {
                    if (Files.getLastModifiedTime(path).toInstant().isBefore(cutoff)) {
                        Files.deleteIfExists(path);
                        removed++;
                    }
                } catch (IOException ignored) {
                    // 单个文件失败不影响其余清理
                }
            }
        } catch (IOException exception) {
            log.warn("TTS 缓存清理失败：reason={}", exception.getClass().getSimpleName());
        }
        if (removed > 0) {
            log.info("TTS 缓存清理完成：removed={} retentionDays={}", removed, cacheDays);
        }
    }

    private String normalizeVoice(String voiceId) {
        if (voiceId == null || voiceId.isBlank()) {
            return defaultVoice;
        }
        String value = voiceId.strip().toLowerCase(Locale.ROOT);
        return "tide".equals(value) || "stella".equals(value) ? value : defaultVoice;
    }

    private Path cachedPath(String hash, String format) {
        if (cacheRoot == null || cacheDays == 0) {
            return null;
        }
        return cacheRoot.resolve(hash + "." + format);
    }

    private byte[] readCache(Path path) {
        try {
            if (!Files.isRegularFile(path)) {
                return null;
            }
            if (Files.getLastModifiedTime(path).toInstant().isBefore(Instant.now().minus(cacheDays, ChronoUnit.DAYS))) {
                Files.deleteIfExists(path);
                return null;
            }
            return Files.readAllBytes(path);
        } catch (IOException exception) {
            log.warn("TTS 缓存读取失败，回源：reason={}", exception.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * 先写唯一临时文件再原子替换，避免同一内容并发合成时抢占同一临时路径。
     */
    private void writeCache(Path path, byte[] bytes) {
        if (path == null) {
            return;
        }
        Path temp = path.resolveSibling(path.getFileName() + "." + UUID.randomUUID() + ".tmp");
        try {
            Files.createDirectories(path.getParent());
            Files.write(temp, bytes);
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            log.warn("TTS 缓存写入失败：reason={}", exception.getClass().getSimpleName());
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
                // 清理失败无需上抛
            }
        }
    }

    private static String contentTypeOf(String format) {
        return "pcm".equals(format) ? "audio/pcm" : "audio/wav";
    }

    /**
     * 合成结果；cached 用于调用日志区分是否回源。
     */
    public record Synthesized(byte[] audio, String contentType, boolean cached) {}
}
