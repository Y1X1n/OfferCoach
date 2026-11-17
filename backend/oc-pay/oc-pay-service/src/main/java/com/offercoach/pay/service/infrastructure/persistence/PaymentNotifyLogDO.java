package com.offercoach.pay.service.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.Data;

/**
 * payment_notify_log 行；回调审计，只记录归一化结果，不存原始报文。
 */
@Data
@TableName("payment_notify_log")
public class PaymentNotifyLogDO {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String orderNo;

    private String channel;

    private String eventType;

    private String providerTxnId;

    private Boolean signatureValid;

    private Boolean processed;

    private String message;

    private OffsetDateTime createdAt;
}
