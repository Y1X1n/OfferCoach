package com.offercoach.pay.service.infrastructure.persistence;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 支付异常队列：同一订单同一类型只保留一条，重复命中不覆盖首次现场。
 */
@Mapper
public interface PaymentExceptionMapper {

    @Insert(
            """
            INSERT INTO payment_exception
                (order_no, user_id, kind, detail, amount_cents, channel, provider_txn_id)
            VALUES
                (#{orderNo}, #{userId}, #{kind}, #{detail}, #{amountCents}, #{channel}, #{providerTxnId})
            ON CONFLICT (order_no, kind) DO NOTHING
            """)
    int insertIfAbsent(
            @Param("orderNo") String orderNo,
            @Param("userId") Long userId,
            @Param("kind") String kind,
            @Param("detail") String detail,
            @Param("amountCents") Integer amountCents,
            @Param("channel") String channel,
            @Param("providerTxnId") String providerTxnId);

    @Select("SELECT COUNT(*) FROM payment_exception WHERE status = 'OPEN'")
    int countOpen();

    @Select(
            """
            SELECT * FROM payment_exception
            WHERE status = 'OPEN'
            ORDER BY created_at ASC
            LIMIT #{limit}
            """)
    List<PaymentExceptionDO> findOpen(@Param("limit") int limit);
}
