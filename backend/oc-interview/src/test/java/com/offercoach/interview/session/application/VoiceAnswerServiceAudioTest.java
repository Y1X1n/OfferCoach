package com.offercoach.interview.session.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.offercoach.common.error.BizException;
import org.junit.jupiter.api.Test;

/**
 * 语音作答的音频魔数校验：只接受 16-bit PCM WAV 或 Ogg Opus。
 */
class VoiceAnswerServiceAudioTest {

    @Test
    void acceptsWavContainer() {
        assertThatCode(() -> VoiceAnswerService.requireSupportedAudio(wav(64))).doesNotThrowAnyException();
    }

    @Test
    void acceptsOggOpus() {
        byte[] ogg = {'O', 'g', 'g', 'S', 0, 2, 0, 0, 0, 0, 0, 0};
        assertThatCode(() -> VoiceAnswerService.requireSupportedAudio(ogg)).doesNotThrowAnyException();
    }

    @Test
    void rejectsArbitraryBytes() {
        assertThatThrownBy(() -> VoiceAnswerService.requireSupportedAudio("not an audio file".getBytes()))
                .isInstanceOf(BizException.class);
    }

    @Test
    void rejectsRiffWithoutWaveSignature() {
        byte[] riffOnly = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'A', 'V', 'I', ' '};
        assertThatThrownBy(() -> VoiceAnswerService.requireSupportedAudio(riffOnly))
                .isInstanceOf(BizException.class);
    }

    @Test
    void rejectsTruncatedHeader() {
        assertThatThrownBy(() -> VoiceAnswerService.requireSupportedAudio(new byte[] {'R', 'I', 'F', 'F'}))
                .isInstanceOf(BizException.class);
    }

    private static byte[] wav(int length) {
        byte[] bytes = new byte[Math.max(length, 12)];
        bytes[0] = 'R';
        bytes[1] = 'I';
        bytes[2] = 'F';
        bytes[3] = 'F';
        bytes[8] = 'W';
        bytes[9] = 'A';
        bytes[10] = 'V';
        bytes[11] = 'E';
        return bytes;
    }
}
