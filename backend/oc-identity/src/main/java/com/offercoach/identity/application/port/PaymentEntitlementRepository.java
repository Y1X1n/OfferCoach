package com.offercoach.identity.application.port;

/**
 * 支付发放幂等登记端口。
 */
public interface PaymentEntitlementRepository {

    /**
     * 幂等登记；返回 true 表示本次为首次登记，false 表示此前已登记。
     */
    boolean tryInsert(String orderNo, long userId, String planCode, int amountCents);
}
