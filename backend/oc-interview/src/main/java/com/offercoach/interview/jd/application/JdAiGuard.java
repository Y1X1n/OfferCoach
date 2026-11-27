package com.offercoach.interview.jd.application;

import com.offercoach.common.cache.RedisKeys;
import com.offercoach.common.time.BusinessDate;
import com.offercoach.identity.application.quota.RedisCounterGuard;
import com.offercoach.interview.config.InterviewProperties;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * JD 场景 AI 调用闸门：按用户按天计数，超限返回 429。
 *
 * <p>只覆盖真正会调用模型的入口（手工粘贴结构化、JD 匹配、链接预览），
 * 模板/启发式降级不额外计数；Redis 不可用时由 {@link RedisCounterGuard} 放行，不阻断主流程。</p>
 */
@Component
public class JdAiGuard {

    private static final Duration TTL = Duration.ofDays(2);

    private final RedisCounterGuard counterGuard;
    private final InterviewProperties properties;

    public JdAiGuard(RedisCounterGuard counterGuard, InterviewProperties properties) {
        this.counterGuard = counterGuard;
        this.properties = properties;
    }

    /**
     * 校验并占用一次当日 AI 配额；限额为 0 时不计数。
     */
    public void checkAndCount(long userId) {
        int limit = properties.getJd().getAiDailyLimit();
        if (limit <= 0) {
            return;
        }
        String key = RedisKeys.dailyJdAi(userId, BusinessDate.todayKey());
        counterGuard.tryAcquireOrThrow(key, limit, TTL, "JD_AI");
    }
}
