package com.offercoach.identity.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserSubscriptionMapper extends BaseMapper<UserSubscriptionDO> {

    /**
     * 结束当前生效订阅；购买新套餐前调用，保持「同一用户仅一条 ACTIVE」。
     */
    @Update(
            """
            UPDATE user_subscription
            SET status = 'EXPIRED'
            WHERE user_id = #{userId} AND status = 'ACTIVE'
            """)
    int expireActive(@Param("userId") long userId);

    /**
     * 自然到期：把已过 {@code expires_at} 的 ACTIVE 订阅置为 EXPIRED。
     *
     * <p>购买新套餐走 {@link #expireActive}（主动替换），这里处理「没人再买」的情况；
     * 不扫的话订阅表会永远停在 ACTIVE，与已经降级到 FREE 的 {@code user_quota} 互相矛盾。</p>
     */
    @Update(
            """
            UPDATE user_subscription
            SET status = 'EXPIRED'
            WHERE user_id = #{userId} AND status = 'ACTIVE' AND expires_at <= #{now}
            """)
    int expireOverdue(@Param("userId") long userId, @Param("now") OffsetDateTime now);

    /**
     * 找出已到期但仍为 ACTIVE 的订阅用户，供定时任务分批结算。
     */
    @Select(
            """
            SELECT user_id FROM user_subscription
            WHERE status = 'ACTIVE' AND expires_at <= #{now}
            ORDER BY expires_at
            LIMIT #{limit}
            """)
    List<Long> findExpiredActiveUserIds(@Param("now") OffsetDateTime now, @Param("limit") int limit);

    @Insert(
            """
            INSERT INTO user_subscription (user_id, plan_code, status, started_at, expires_at, auto_renew)
            VALUES (#{userId}, #{planCode}, 'ACTIVE', #{startedAt}, #{expiresAt}, false)
            """)
    int insertActive(
            @Param("userId") long userId,
            @Param("planCode") String planCode,
            @Param("startedAt") OffsetDateTime startedAt,
            @Param("expiresAt") OffsetDateTime expiresAt);
}
