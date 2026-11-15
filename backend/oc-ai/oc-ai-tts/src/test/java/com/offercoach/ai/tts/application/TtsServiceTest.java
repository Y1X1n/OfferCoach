package com.offercoach.ai.tts.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offercoach.ai.tools.client.DeepSeekToolsClient;
import com.offercoach.ai.tools.config.ToolsProperties;
import com.offercoach.ai.tools.error.ToolErrorKind;
import com.offercoach.ai.tools.error.ToolException;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * TTS 合成缓存：按内容哈希命中不回源、入参上限与过期清理。
 */
class TtsServiceTest {

    private static final byte[] AUDIO = {1, 2, 3, 4, 5};

    @TempDir
    Path tempDir;

    @Test
    void secondCallHitsCacheWithoutHittingUpstream() {
        CountingClient client = new CountingClient();
        TtsService service = new TtsService(client, properties(7));

        TtsService.Synthesized first = service.synthesize("你好，请自我介绍", "stella", "wav");
        TtsService.Synthesized second = service.synthesize("你好，请自我介绍", "stella", "wav");

        assertThat(first.cached()).isFalse();
        assertThat(second.cached()).isTrue();
        assertThat(second.audio()).isEqualTo(AUDIO);
        assertThat(client.calls).isEqualTo(1);
    }

    @Test
    void differentVoiceMissesCache() {
        CountingClient client = new CountingClient();
        TtsService service = new TtsService(client, properties(7));

        service.synthesize("同一段文本", "stella", "wav");
        service.synthesize("同一段文本", "tide", "wav");

        assertThat(client.calls).isEqualTo(2);
    }

    @Test
    void unknownVoiceFallsBackToDefault() {
        CountingClient client = new CountingClient();
        TtsService service = new TtsService(client, properties(7));

        service.synthesize("文本", "unknown-voice", "wav");
        service.synthesize("文本", "stella", "wav");

        assertThat(client.calls).isEqualTo(1);
    }

    @Test
    void rejectsBlankText() {
        TtsService service = new TtsService(new CountingClient(), properties(7));

        assertThatThrownBy(() -> service.synthesize("   ", "stella", "wav"))
                .isInstanceOf(ToolException.class)
                .satisfies(exception ->
                        assertThat(((ToolException) exception).kind()).isEqualTo(ToolErrorKind.INVALID_INPUT));
    }

    @Test
    void rejectsTextOverCharacterLimit() {
        TtsService service = new TtsService(new CountingClient(), properties(7));

        assertThatThrownBy(() -> service.synthesize("x".repeat(10_001), "stella", "wav"))
                .isInstanceOf(ToolException.class);
    }

    @Test
    void purgeRemovesExpiredCacheEntries() throws Exception {
        TtsService service = new TtsService(new CountingClient(), properties(1));
        service.synthesize("你好", "stella", "wav");
        Path cacheFile = onlyFile();

        Files.setLastModifiedTime(cacheFile, FileTime.from(Instant.now().minus(3, ChronoUnit.DAYS)));
        service.purgeExpiredCache();

        assertThat(fileCount()).isZero();
    }

    @Test
    void purgeKeepsFreshCacheEntries() throws Exception {
        TtsService service = new TtsService(new CountingClient(), properties(7));
        service.synthesize("你好", "stella", "wav");

        service.purgeExpiredCache();

        assertThat(fileCount()).isEqualTo(1);
    }

    private Path onlyFile() throws Exception {
        try (Stream<Path> stream = Files.list(tempDir)) {
            return stream.findFirst().orElseThrow();
        }
    }

    private long fileCount() throws Exception {
        try (Stream<Path> stream = Files.list(tempDir)) {
            return stream.filter(Files::isRegularFile).count();
        }
    }

    private ToolsProperties properties(int cacheDays) {
        ToolsProperties properties = new ToolsProperties();
        properties.setTtsCacheDir(tempDir.toString());
        properties.setTtsCacheDays(cacheDays);
        properties.setTtsDefaultVoice("stella");
        return properties;
    }

    /**
     * 计数用的假客户端：不触网，固定返回音频字节。
     */
    private static final class CountingClient extends DeepSeekToolsClient {

        private int calls;

        private CountingClient() {
            super(null, new ToolsProperties(), new ObjectMapper());
        }

        @Override
        public AudioStream tts(String text, String voiceId, String format) {
            calls++;
            return new AudioStream(new ByteArrayInputStream(AUDIO), "audio/wav");
        }
    }
}
