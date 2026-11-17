package com.offercoach.pay.service.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.Data;

/**
 * payment_order 行；支付订单事实源，由 oc-pay-service 独占写入。
 */
@Data
@TableName("payment_order")
public class PaymentOrderDO {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String orderNo;

    private Long userId;

    private String planCode;

    private String subject;

    private Integer amountCents;

    private String currency;

    private String channel;

    private String channelMethod;

    private String status;

    private String providerTxnId;

    private String prepayId;

    private String codeUrl;

    private String idempotencyKey;

    private String notifyStatus;

    private Integer notifyAttempts;

    private OffsetDateTime expiresAt;

    private OffsetDateTime paidAt;

    private OffsetDateTime closedAt;

    private Integer refundedCents;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
