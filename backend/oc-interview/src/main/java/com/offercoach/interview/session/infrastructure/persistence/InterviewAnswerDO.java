package com.offercoach.interview.session.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Data;

/**
 * interview_answer 表映射；answer_text 禁止写入日志。
 */
@Data
@TableName("interview_answer")
public class InterviewAnswerDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    private Long questionId;

    private Long userId;

    private String inputMode;

    private String answerText;

    private Integer durationMs;

    private String status;

    private OffsetDateTime submittedAt;

    private OffsetDateTime createdAt;

    private String audioKey;

    private Integer audioDurationMs;

    private String asrStatus;

    private BigDecimal asrConfidence;
}
