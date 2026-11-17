package com.offercoach.pay.service.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PaymentRefundMapper extends BaseMapper<PaymentRefundDO> {

    @Select("SELECT * FROM payment_refund WHERE refund_no = #{refundNo}")
    PaymentRefundDO findByRefundNo(@Param("refundNo") String refundNo);

    @Select("SELECT * FROM payment_refund WHERE order_no = #{orderNo} AND idempotency_key = #{idempotencyKey}")
    PaymentRefundDO findByOrderIdem(@Param("orderNo") String orderNo, @Param("idempotencyKey") String idempotencyKey);

    @Update(
            """
            UPDATE payment_refund
            SET status = #{status}, channel_refund_id = #{channelRefundId}, updated_at = now()
            WHERE refund_no = #{refundNo}
            """)
    int updateResult(
            @Param("refundNo") String refundNo,
            @Param("status") String status,
            @Param("channelRefundId") String channelRefundId);
}
