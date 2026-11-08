package com.offercoach.interview.qbank;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * question_bank 访问：检索只取 PUBLISHED，导入以 (source_repo, source_path) 幂等。
 */
@Mapper
public interface QuestionBankMapper {

    @Select(
            """
            SELECT id, title, job_family, dimension, difficulty,
                   array_to_string(tags, chr(31)) AS tags_joined,
                   stem, reference_answer, scoring_points::text AS scoring_points,
                   source_repo, company, quality_score
            FROM question_bank
            WHERE status = 'PUBLISHED' AND lower(job_family) = lower(#{jobFamily})
            ORDER BY (CASE WHEN reference_answer IS NOT NULL AND btrim(reference_answer) <> '' THEN 0 ELSE 1 END), id
            LIMIT #{limit}
            """)
    List<QuestionBankDO> selectPublishedByFamily(@Param("jobFamily") String jobFamily, @Param("limit") int limit);

    @Select(
            """
            SELECT id, title, job_family, dimension, difficulty,
                   array_to_string(tags, chr(31)) AS tags_joined,
                   stem, reference_answer, scoring_points::text AS scoring_points,
                   source_repo, company, quality_score
            FROM question_bank
            WHERE status = 'PUBLISHED'
            ORDER BY (CASE WHEN reference_answer IS NOT NULL AND btrim(reference_answer) <> '' THEN 0 ELSE 1 END), id
            LIMIT #{limit}
            """)
    List<QuestionBankDO> selectPublished(@Param("limit") int limit);

    @Insert(
            """
            INSERT INTO question_bank
                (title, job_family, dimension, difficulty, tags, stem, reference_answer, scoring_points,
                 source, source_repo, source_path, source_url, source_license, company, language, status,
                 embedding_status, created_at, updated_at)
            VALUES
                (#{title}, #{jobFamily}, #{dimension}, #{difficulty},
                 CASE WHEN #{tagsJoined} = '' THEN '{}'::text[] ELSE string_to_array(#{tagsJoined}, chr(31)) END,
                 #{stem}, #{referenceAnswer}, CAST(#{scoringPoints,jdbcType=VARCHAR} AS jsonb),
                 'IMPORT', #{sourceRepo}, #{sourcePath}, #{sourceUrl}, #{sourceLicense}, #{company}, #{language},
                 'PUBLISHED', 'READY', now(), now())
            ON CONFLICT (source_repo, source_path, stem_hash) DO NOTHING
            """)
    int insertImported(
            @Param("title") String title,
            @Param("jobFamily") String jobFamily,
            @Param("dimension") String dimension,
            @Param("difficulty") String difficulty,
            @Param("tagsJoined") String tagsJoined,
            @Param("stem") String stem,
            @Param("referenceAnswer") String referenceAnswer,
            @Param("scoringPoints") String scoringPoints,
            @Param("sourceRepo") String sourceRepo,
            @Param("sourcePath") String sourcePath,
            @Param("sourceUrl") String sourceUrl,
            @Param("sourceLicense") String sourceLicense,
            @Param("company") String company,
            @Param("language") String language);
}
