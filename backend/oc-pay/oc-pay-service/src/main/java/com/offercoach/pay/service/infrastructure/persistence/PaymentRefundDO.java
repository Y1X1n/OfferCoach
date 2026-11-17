package com.offercoach.pay.service.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.Data;

/**
 * payment_refund 行；退款记录，order_no + idempotency_key 唯一。
 */
@Data
@TableName("payment_refund")
public class PaymentRefundDO {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String refundNo;

    private String orderNo;

    private Long userId;

    private Integer amountCents;

    private String status;

    private String channelRefundId;

    private String reason;

    private String idempotencyKey;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
