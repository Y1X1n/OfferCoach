package com.offercoach.identity.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.Data;

/**
 * user_subscription 行；同一用户仅一条 ACTIVE。
 */
@Data
@TableName("user_subscription")
public class UserSubscriptionDO {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String planCode;

    private String status;

    private OffsetDateTime startedAt;

    private OffsetDateTime expiresAt;

    private Boolean autoRenew;

    private OffsetDateTime createdAt;
}
