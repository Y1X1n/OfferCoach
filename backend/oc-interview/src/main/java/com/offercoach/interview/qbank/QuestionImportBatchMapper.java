package com.offercoach.interview.qbank;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * question_import_batch 统计写入。
 */
@Mapper
public interface QuestionImportBatchMapper {

    @Insert(
            """
            INSERT INTO question_import_batch
                (source_repo, total_count, success_count, failed_count, skipped_count, status,
                 error_report, created_at, finished_at)
            VALUES
                (#{sourceRepo}, #{total}, #{success}, #{failed}, #{skipped}, 'DONE',
                 CAST(#{errorReport,jdbcType=VARCHAR} AS jsonb), now(), now())
            """)
    int insertBatch(
            @Param("sourceRepo") String sourceRepo,
            @Param("total") int total,
            @Param("success") int success,
            @Param("failed") int failed,
            @Param("skipped") int skipped,
            @Param("errorReport") String errorReport);
}
