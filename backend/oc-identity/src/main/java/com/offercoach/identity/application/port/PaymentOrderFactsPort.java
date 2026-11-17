package com.offercoach.identity.application.port;

import java.util.Optional;

/**
 * 支付订单事实查询端口：发放授信前的交叉校验数据来源。
 *
 * <p>订单由 oc-pay-service 写入，本端口只读；实现放在 infrastructure，避免 application 依赖持久化细节。</p>
 */
public interface PaymentOrderFactsPort {

    Optional<Facts> find(String orderNo);

    /**
     * 发放校验所需的最小事实集合。
     */
    record Facts(long userId, String planCode, int amountCents, String status) {}
}
