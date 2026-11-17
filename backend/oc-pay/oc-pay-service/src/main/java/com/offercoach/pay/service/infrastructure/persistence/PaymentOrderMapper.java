package com.offercoach.pay.service.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PaymentOrderMapper extends BaseMapper<PaymentOrderDO> {

    @Select("SELECT * FROM payment_order WHERE order_no = #{orderNo}")
    PaymentOrderDO findByOrderNo(@Param("orderNo") String orderNo);

    @Select("SELECT * FROM payment_order WHERE order_no = #{orderNo} FOR UPDATE")
    PaymentOrderDO selectForUpdate(@Param("orderNo") String orderNo);

    @Select("SELECT * FROM payment_order WHERE user_id = #{userId} AND idempotency_key = #{idempotencyKey}")
    PaymentOrderDO findByUserIdem(@Param("userId") long userId, @Param("idempotencyKey") String idempotencyKey);

    /**
     * 回写渠道下单结果；仅待支付订单可写。
     */
    @Update(
            """
            UPDATE payment_order
            SET code_url = #{codeUrl}, prepay_id = #{prepayId}, updated_at = now()
            WHERE order_no = #{orderNo} AND status = 'CREATED'
            """)
    int attachPrepay(
            @Param("orderNo") String orderNo, @Param("codeUrl") String codeUrl, @Param("prepayId") String prepayId);

    /**
     * 标记已支付；已支付或非待支付时影响 0 行（幂等）。
     */
    @Update(
            """
            UPDATE payment_order
            SET status = 'PAID', provider_txn_id = #{providerTxnId}, paid_at = #{paidAt},
                notify_status = 'PENDING', updated_at = now()
            WHERE order_no = #{orderNo} AND status = 'CREATED'
            """)
    int markPaid(
            @Param("orderNo") String orderNo,
            @Param("providerTxnId") String providerTxnId,
            @Param("paidAt") OffsetDateTime paidAt);

    @Update(
            """
            UPDATE payment_order
            SET status = 'CLOSED', closed_at = now(), updated_at = now()
            WHERE order_no = #{orderNo} AND status = 'CREATED'
            """)
    int markClosed(@Param("orderNo") String orderNo);

    @Update(
            """
            UPDATE payment_order
            SET status = 'FAILED', updated_at = now()
            WHERE order_no = #{orderNo} AND status = 'CREATED'
            """)
    int markFailed(@Param("orderNo") String orderNo);

    /**
     * 原子预占可退金额：条件更新，超出剩余可退金额或非已支付状态时影响 0 行。
     * 并发退款的安全性由本语句（而非仅行锁）保证。
     */
    @Update(
            """
            UPDATE payment_order
            SET refunded_cents = refunded_cents + #{amountCents}, updated_at = now()
            WHERE order_no = #{orderNo}
              AND status IN ('PAID', 'REFUNDED')
              AND refunded_cents + #{amountCents} <= amount_cents
            """)
    int reserveRefundAmount(@Param("orderNo") String orderNo, @Param("amountCents") int amountCents);

    /**
     * 退款成功后将退满的订单标记为 REFUNDED；部分退款保持 PAID。
     */
    @Update(
            """
            UPDATE payment_order
            SET status = CASE WHEN refunded_cents >= amount_cents THEN 'REFUNDED' ELSE status END,
                updated_at = now()
            WHERE order_no = #{orderNo} AND status = 'PAID'
            """)
    int finalizeRefund(@Param("orderNo") String orderNo);

    /**
     * 渠道退款失败时归还预占金额。
     */
    @Update(
            """
            UPDATE payment_order
            SET refunded_cents = refunded_cents - #{amountCents}, updated_at = now()
            WHERE order_no = #{orderNo} AND refunded_cents >= #{amountCents}
            """)
    int releaseRefundAmount(@Param("orderNo") String orderNo, @Param("amountCents") int amountCents);

    /**
     * 更新通知状态与尝试次数；FAILED 且已达最大尝试次数时升级为 DEAD（终态，不再重试）。
     */
    @Update(
            """
            UPDATE payment_order
            SET notify_status = CASE
                    WHEN #{status} = 'FAILED' AND notify_attempts + 1 >= #{maxAttempts} THEN 'DEAD'
                    ELSE #{status}
                END,
                notify_attempts = notify_attempts + 1,
                updated_at = now()
            WHERE order_no = #{orderNo}
            """)
    int updateNotify(
            @Param("orderNo") String orderNo, @Param("status") String status, @Param("maxAttempts") int maxAttempts);

    @Select(
            """
            SELECT * FROM payment_order
            WHERE status = 'CREATED' AND expires_at < now()
            ORDER BY expires_at ASC
            LIMIT #{limit}
            """)
    List<PaymentOrderDO> findExpired(@Param("limit") int limit);

    /**
     * 待核对支付状态的订单：仍待支付且已过冷却期，用于回调缺失时主动对账。
     */
    @Select(
            """
            SELECT * FROM payment_order
            WHERE status = 'CREATED' AND created_at < #{createdBefore} AND expires_at > now()
            ORDER BY created_at ASC
            LIMIT #{limit}
            """)
    List<PaymentOrderDO> findReconcileCandidates(
            @Param("createdBefore") OffsetDateTime createdBefore, @Param("limit") int limit);

    @Select(
            """
            SELECT * FROM payment_order
            WHERE status = 'PAID' AND notify_status IN ('PENDING', 'FAILED') AND notify_attempts < #{maxAttempts}
            ORDER BY updated_at ASC
            LIMIT #{limit}
            """)
    List<PaymentOrderDO> findPendingNotify(@Param("maxAttempts") int maxAttempts, @Param("limit") int limit);

    @Insert(
            """
            INSERT INTO payment_notify_log
                (order_no, channel, event_type, provider_txn_id, signature_valid, processed, message, created_at)
            VALUES
                (#{orderNo}, #{channel}, #{eventType}, #{providerTxnId},
                 #{signatureValid}, #{processed}, #{message}, now())
            """)
    int insertNotifyLog(
            @Param("orderNo") String orderNo,
            @Param("channel") String channel,
            @Param("eventType") String eventType,
            @Param("providerTxnId") String providerTxnId,
            @Param("signatureValid") boolean signatureValid,
            @Param("processed") boolean processed,
            @Param("message") String message);
}
