package com.offercoach.identity.domain.quota;

/**
 * quota_ledger 行的领域投影；append-only 流水，user_id + idempotency_key 唯一。
 */
public record QuotaLedgerEntry(
        Long id,
        long userId,
        String quotaType,
        int delta,
        int balanceAfter,
        String reason,
        String refType,
        Long refId,
        String idempotencyKey) {}
