package com.offercoach.interview.qbank;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import lombok.Data;

/**
 * question_bank 行；tags 以 chr(31) 拼接字符串出入，避免依赖数组类型处理器。
 */
@Data
@TableName("question_bank")
public class QuestionBankDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String jobFamily;

    private String dimension;

    private String difficulty;

    private String tagsJoined;

    private String stem;

    private String referenceAnswer;

    private String scoringPoints;

    private String source;

    private String sourceRepo;

    private String sourcePath;

    private String sourceUrl;

    private String sourceLicense;

    private String company;

    private String language;

    private String status;

    private BigDecimal qualityScore;
}
