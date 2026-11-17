package com.offercoach.identity.infrastructure.persistence;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * payment_entitlement 映射；仅用于支付发放的幂等登记。
 */
@Mapper
public interface PaymentEntitlementMapper {

    /**
     * 幂等登记支付发放；同一订单只成功一次，冲突返回 0 行。
     */
    @Insert(
            """
            INSERT INTO payment_entitlement (order_no, user_id, plan_code, amount_cents)
            VALUES (#{orderNo}, #{userId}, #{planCode}, #{amountCents})
            ON CONFLICT (order_no) DO NOTHING
            """)
    int insertIfAbsent(
            @Param("orderNo") String orderNo,
            @Param("userId") long userId,
            @Param("planCode") String planCode,
            @Param("amountCents") int amountCents);
}
