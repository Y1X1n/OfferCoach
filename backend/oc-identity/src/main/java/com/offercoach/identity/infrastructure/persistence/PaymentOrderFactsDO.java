package com.offercoach.identity.infrastructure.persistence;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 支付订单事实（只读）：发放授信前用于交叉校验，防止仅凭内部通知内容发放套餐。
 */
@Data
@NoArgsConstructor
public class PaymentOrderFactsDO {

    private Long userId;

    private String planCode;

    private Integer amountCents;

    private String status;
}
