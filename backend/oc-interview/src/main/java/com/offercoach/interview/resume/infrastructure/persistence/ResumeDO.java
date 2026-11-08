package com.offercoach.interview.resume.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.Data;

/**
 * resume 表映射；JSONB 列在 SQL 中以文本形式出入。
 */
@Data
@TableName("resume")
public class ResumeDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String title;

    private String sourceType;

    private String fileKey;

    private String fileName;

    private String mimeType;

    private Long fileSize;

    private String sha256;

    private Integer pageCount;

    private String parseStatus;

    private String parseError;

    private String rawText;

    private String structuredJson;

    private String summary;

    private String summaryHash;

    private Boolean defaultFlag;

    private OffsetDateTime deletedAt;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
