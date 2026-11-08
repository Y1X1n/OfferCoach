package com.offercoach.identity.domain.quota;

import java.time.LocalDate;

/**
 * user_quota 行的领域投影；余额快照，事实源为 quota_ledger。
 */
public record QuotaAccount(
        long userId,
        String planCode,
        int interviewCredits,
        int resumeCredits,
        LocalDate periodStart,
        LocalDate periodEnd,
        LocalDate dailySessionDate,
        int dailySessionCount) {

    /**
     * 返回替换每日会话日期与计数的副本；快照跨天重置后使用。
     */
    public QuotaAccount withDailySession(LocalDate date, int count) {
        return new QuotaAccount(userId, planCode, interviewCredits, resumeCredits, periodStart, periodEnd, date, count);
    }
}
