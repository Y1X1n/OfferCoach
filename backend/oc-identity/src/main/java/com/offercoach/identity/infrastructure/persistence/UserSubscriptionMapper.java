package com.offercoach.identity.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.OffsetDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
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
