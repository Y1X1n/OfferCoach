package com.offercoach.interview.report.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Data;

/**
 * interview_report 表映射；结构型字段以 JSONB 文本出入。
 */
@Data
@TableName("interview_report")
public class InterviewReportDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    private Long userId;

    private String status;

    private BigDecimal overallScore;

    private String radarJson;

    private String summary;

    private String highlightsJson;

    private String risksJson;

    private String improvementPlanJson;

    private String questionBreakdownJson;

    private Integer attempts;

    private String lastError;

    private OffsetDateTime generatedAt;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
