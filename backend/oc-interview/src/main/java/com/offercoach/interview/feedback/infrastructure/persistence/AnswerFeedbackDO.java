package com.offercoach.interview.feedback.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Data;

/**
 * answer_feedback 表映射；dimensions/strengths/improvements/evidence 以 JSONB 文本出入。
 */
@Data
@TableName("answer_feedback")
public class AnswerFeedbackDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long answerId;

    private Integer version;

    private String requestId;

    private Long sessionId;

    private Long userId;

    private BigDecimal overallScore;

    private String dimensionsJson;

    private String strengthsJson;

    private String improvementsJson;

    private String evidenceJson;

    private String rewrittenAnswer;

    private String model;

    private String provider;

    private Boolean degraded;

    private OffsetDateTime createdAt;
}
