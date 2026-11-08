package com.offercoach.identity.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SubscriptionPlanMapper extends BaseMapper<SubscriptionPlanDO> {

    /**
     * JSONB 直接映射为 String 依赖驱动细节，这里显式 CAST 成 text，保证读出一致。
     */
    @Select(
            """
            SELECT code, name, price_cents, period_days, interview_credits, resume_credits,
                   CAST(features AS text) AS features, active
            FROM subscription_plan
            WHERE code = #{code}
            """)
    SubscriptionPlanDO findByCode(@Param("code") String code);

    @Select(
            """
            SELECT code, name, price_cents, period_days, interview_credits, resume_credits,
                   CAST(features AS text) AS features, active
            FROM subscription_plan
            WHERE active = true
            ORDER BY price_cents ASC, code ASC
            """)
    List<SubscriptionPlanDO> listActive();
}
