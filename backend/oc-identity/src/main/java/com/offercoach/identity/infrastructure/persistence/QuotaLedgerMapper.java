package com.offercoach.identity.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface QuotaLedgerMapper extends BaseMapper<QuotaLedgerDO> {

    /**
     * 按幂等键查流水；调用方必须已持有 user_quota 行锁。
     */
    @Select(
            """
            SELECT id, user_id, quota_type, delta, balance_after, reason,
                   ref_type, ref_id, idempotency_key, created_at
            FROM quota_ledger
            WHERE user_id = #{userId} AND idempotency_key = #{idempotencyKey}
            """)
    QuotaLedgerDO findByIdemKey(@Param("userId") long userId, @Param("idempotencyKey") String idempotencyKey);

    /**
     * 幂等追加流水：周期续发等可重入路径使用，冲突时静默忽略。
     */
    @Insert(
            """
            INSERT INTO quota_ledger
                (user_id, quota_type, delta, balance_after, reason, ref_type, ref_id, idempotency_key)
            VALUES
                (#{userId}, #{quotaType}, #{delta}, #{balanceAfter}, #{reason}, #{refType}, #{refId},
                 #{idempotencyKey})
            ON CONFLICT (user_id, idempotency_key) DO NOTHING
            """)
    int insertIfAbsent(
            @Param("userId") long userId,
            @Param("quotaType") String quotaType,
            @Param("delta") int delta,
            @Param("balanceAfter") int balanceAfter,
            @Param("reason") String reason,
            @Param("refType") String refType,
            @Param("refId") Long refId,
            @Param("idempotencyKey") String idempotencyKey);
}
