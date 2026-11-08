package com.offercoach.identity.application.quota;

/**
 * 额度扣减 / 返还结果。
 *
 * @param charged      本次调用是否真正变更了余额
 * @param balanceAfter 变更后的余额（幂等重放时为流水中的 balance_after）
 * @param replayed     是否命中已有幂等流水
 */
public record QuotaResult(boolean charged, int balanceAfter, boolean replayed) {

    public static QuotaResult applied(int balanceAfter) {
        return new QuotaResult(true, balanceAfter, false);
    }

    public static QuotaResult alreadyProcessed(int balanceAfter) {
        return new QuotaResult(false, balanceAfter, true);
    }
}
