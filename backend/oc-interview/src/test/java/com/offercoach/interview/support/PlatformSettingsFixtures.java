package com.offercoach.interview.support;

import com.offercoach.identity.application.settings.PlatformSettingsService;

/**
 * 平台策略测试夹具：只关心 AI 工具四项上限，其余取默认占位值。
 */
public final class PlatformSettingsFixtures {

    private PlatformSettingsFixtures() {}

    public static PlatformSettingsService.Snapshot snapshot(
            int ttsPerQuestion, int ocrDaily, int searchDaily, int fetchDaily) {
        return new PlatformSettingsService.Snapshot(
                true, 5, 5, 10, 5, 1, 1, 3, 30, 5, 5, ttsPerQuestion, ocrDaily, searchDaily, fetchDaily, null, null);
    }
}
