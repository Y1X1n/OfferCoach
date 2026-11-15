package com.offercoach.interview.session.application;

import com.offercoach.interview.config.InterviewProperties;
import com.offercoach.interview.storage.FileStorage;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 语音作答音频保留期清理：避免 voice/ 目录无限增长。
 *
 * <p>默认保留 7 天（`oc.interview.voice-retention-days`，0 表示不自动删除）；
 * 与日志清理同批在低峰执行。</p>
 */
@Component
public class VoiceAudioRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(VoiceAudioRetentionJob.class);
    private static final String VOICE_PREFIX = "voice/";

    private final FileStorage fileStorage;
    private final InterviewProperties properties;

    public VoiceAudioRetentionJob(FileStorage fileStorage, InterviewProperties properties) {
        this.fileStorage = fileStorage;
        this.properties = properties;
    }

    @Scheduled(cron = "${oc.retention.cron:0 30 3 * * *}", zone = "${oc.retention.zone:Asia/Shanghai}")
    public void purgeExpired() {
        int days = properties.getVoiceRetentionDays();
        if (days <= 0) {
            return;
        }
        Instant cutoff = Instant.now().minus(days, ChronoUnit.DAYS);
        int removed = 0;
        for (String key : fileStorage.listKeys(VOICE_PREFIX)) {
            Instant modified = fileStorage.lastModified(key);
            if (modified != null && modified.isBefore(cutoff)) {
                fileStorage.delete(key);
                removed++;
            }
        }
        if (removed > 0) {
            log.info("语音音频清理完成：removed={} retentionDays={}", removed, days);
        }
    }
}
