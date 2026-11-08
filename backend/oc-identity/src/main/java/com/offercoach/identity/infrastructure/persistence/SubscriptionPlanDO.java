package com.offercoach.identity.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * subscription_plan 行；features 为 JSONB，查询时在 SQL 中 CAST 成 text 接收。
 */
@Data
@TableName("subscription_plan")
public class SubscriptionPlanDO {

    @TableId(value = "code", type = IdType.INPUT)
    private String code;

    private String name;

    private Integer priceCents;

    private Integer periodDays;

    private Integer interviewCredits;

    private Integer resumeCredits;

    private String features;

    private Boolean active;
}
