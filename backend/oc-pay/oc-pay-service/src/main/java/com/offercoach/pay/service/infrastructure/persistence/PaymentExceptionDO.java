package com.offercoach.pay.service.infrastructure.persistence;

import java.time.OffsetDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 支付异常队列行；只读展示用，写入统一走 Mapper 的 insertIfAbsent。
 */
@Data
@NoArgsConstructor
public class PaymentExceptionDO {

    private Long id;

    private String orderNo;

    private Long userId;

    private String kind;

    private String detail;

    private Integer amountCents;

    private String channel;

    private String providerTxnId;

    private String status;

    private OffsetDateTime createdAt;

    private OffsetDateTime resolvedAt;
}
