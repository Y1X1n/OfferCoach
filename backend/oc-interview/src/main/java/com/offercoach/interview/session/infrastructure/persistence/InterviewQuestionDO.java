package com.offercoach.interview.session.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.Data;

/**
 * interview_question 表映射；三个 JSONB 列以文本出入。
 */
@Data
@TableName("interview_question")
public class InterviewQuestionDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    private Integer seq;

    private String dimension;

    private String difficulty;

    private String questionText;

    private String source;

    private Long bankQuestionId;

    private Long parentQuestionId;

    private String referencePointsJson;

    private String contextJson;

    private String whyThisQuestion;

    private String retrievalJson;

    private String status;

    private OffsetDateTime askedAt;

    private OffsetDateTime createdAt;
}
