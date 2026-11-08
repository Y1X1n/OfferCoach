package com.offercoach.identity.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.Data;

/**
 * quota_ledger 行；append-only 流水，user_id + idempotency_key 唯一。
 */
@Data
@TableName("quota_ledger")
public class QuotaLedgerDO {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String quotaType;

    private Integer delta;

    private Integer balanceAfter;

    private String reason;

    private String refType;

    private Long refId;

    private String idempotencyKey;

    private OffsetDateTime createdAt;
}
