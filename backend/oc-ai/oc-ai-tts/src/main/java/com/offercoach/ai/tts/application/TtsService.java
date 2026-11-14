package com.offercoach.ai.tts.application;

import com.offercoach.ai.tools.client.DeepSeekToolsClient;
import com.offercoach.ai.tools.config.ToolsProperties;
import com.offercoach.ai.tools.error.ToolErrorKind;
import com.offercoach.ai.tools.error.ToolException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
        this.cacheRoot = dir == null || dir.isBlank()
                ? null
                : Paths.get(dir).toAbsolutePath().normalize();
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
        String hash = digest(normalizedText + '\u0000' + voice + '\u0000' + audioFormat);
        Path cached = cachedPath(hash, audioFormat);
        if (cached != null) {
            byte[] bytes = readCache(cached);
            if (bytes != null) {
                return new Synthesized(bytes, contentTypeOf(audioFormat), true);
            }
        }
        if (normalizedText.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_BYTES) {
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

    public String defaultVoice() {
        return defaultVoice;
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
            if (Files.getLastModifiedTime(path)
                    .toInstant()
                    .isBefore(java.time.Instant.now().minus(cacheDays, java.time.temporal.ChronoUnit.DAYS))) {
                Files.deleteIfExists(path);
                return null;
            }
            return Files.readAllBytes(path);
        } catch (IOException exception) {
            log.warn("TTS 缓存读取失败，回源：reason={}", exception.getClass().getSimpleName());
            return null;
        }
    }

    private void writeCache(Path path, byte[] bytes) {
        if (path == null) {
            return;
        }
        try {
            Files.createDirectories(path.getParent());
            Path temp = path.resolveSibling(path.getFileName() + ".tmp");
            Files.write(temp, bytes);
            Files.move(temp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            log.warn("TTS 缓存写入失败：reason={}", exception.getClass().getSimpleName());
        }
    }

    private static String contentTypeOf(String format) {
        return "pcm".equals(format) ? "audio/pcm" : "audio/wav";
    }

    private static String digest(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    /**
     * 合成结果；cached 用于调用日志区分是否回源。
     */
    public record Synthesized(byte[] audio, String contentType, boolean cached) {}
}
