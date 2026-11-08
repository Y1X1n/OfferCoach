package com.offercoach.interview.session.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Data;

/**
 * interview_session 表映射；config 以 JSONB 文本出入。
 */
@Data
@TableName("interview_session")
public class InterviewSessionDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long resumeId;

    private Long jdId;

    private String status;

    private String configJson;

    private Integer questionCount;

    private Integer answeredCount;

    private Integer currentSeq;

    private BigDecimal totalScore;

    private String failureReason;

    private String idempotencyKey;

    private OffsetDateTime startedAt;

    private OffsetDateTime finishedAt;

    private OffsetDateTime lastActiveAt;

    private OffsetDateTime expiresAt;

    private OffsetDateTime createdAt;
}
