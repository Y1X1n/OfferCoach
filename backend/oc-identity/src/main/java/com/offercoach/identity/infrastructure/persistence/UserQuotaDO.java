package com.offercoach.identity.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.Data;

/**
 * user_quota 行；余额快照，事实源为 quota_ledger。
 */
@Data
@TableName("user_quota")
public class UserQuotaDO {

    @TableId(value = "user_id", type = IdType.INPUT)
    private Long userId;

    private String planCode;

    private Integer interviewCredits;

    private Integer resumeCredits;

    private LocalDate periodStart;

    private LocalDate periodEnd;

    private LocalDate dailySessionDate;

    private Integer dailySessionCount;

    private OffsetDateTime updatedAt;

    private Integer version;
}
