package com.offercoach.pay.service.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
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

    /**
     * 幂等插入：唯一键冲突时静默跳过并返回 0。
     *
     * <p>不能用「INSERT 抛 DuplicateKeyException → 捕获后回查」的写法：PostgreSQL 在唯一约束冲突后会把
     * 整个事务置为 aborted，同一事务里的后续 SELECT 必然失败（current transaction is aborted），
     * 并发重复退款因此会变成 500 而不是幂等返回。</p>
     */
    @Insert(
            """
            INSERT INTO payment_refund
                (refund_no, order_no, user_id, amount_cents, status, reason, idempotency_key, created_at, updated_at)
            VALUES (#{refundNo}, #{orderNo}, #{userId}, #{amountCents}, #{status}, #{reason},
                    #{idempotencyKey}, now(), now())
            ON CONFLICT DO NOTHING
            """)
    int insertIfAbsent(
            @Param("refundNo") String refundNo,
            @Param("orderNo") String orderNo,
            @Param("userId") long userId,
            @Param("amountCents") int amountCents,
            @Param("status") String status,
            @Param("reason") String reason,
            @Param("idempotencyKey") String idempotencyKey);

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
