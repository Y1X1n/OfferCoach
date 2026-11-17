package com.offercoach.identity.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 支付订单只读查询；payment_order 由 oc-pay-service 写入，本接口只做发放前的交叉校验。
 */
@Mapper
public interface PaymentOrderLookupMapper {

    @Select(
            """
            SELECT user_id, plan_code, amount_cents, status
            FROM payment_order
            WHERE order_no = #{orderNo}
            """)
    PaymentOrderFactsDO findFacts(@Param("orderNo") String orderNo);
}
