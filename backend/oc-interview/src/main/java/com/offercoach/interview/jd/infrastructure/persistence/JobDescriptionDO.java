package com.offercoach.interview.jd.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Data;

/**
 * job_description 表映射。
 */
@Data
@TableName("job_description")
public class JobDescriptionDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String title;

    private String company;

    private String rawText;

    private String structuredJson;

    private BigDecimal matchScore;

    private String matchReport;

    private String contentHash;

    private String sourceType;

    private String sourceUrl;

    private OffsetDateTime deletedAt;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
