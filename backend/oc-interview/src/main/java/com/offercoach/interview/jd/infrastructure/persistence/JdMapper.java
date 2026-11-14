package com.offercoach.interview.jd.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * job_description 表访问；JSONB 列以文本形式出入。
 */
@Mapper
public interface JdMapper {

    String COLUMNS =
            """
            id, user_id, title, company, raw_text, structured_json::text AS structured_json, match_score,
            match_report::text AS match_report, content_hash, created_at, source_type, source_url
            """;

    @Select("SELECT " + COLUMNS
            + " FROM job_description WHERE id = #{id} AND user_id = #{userId} AND deleted_at IS NULL")
    JobDescriptionDO findOwned(@Param("userId") long userId, @Param("id") long id);

    @Insert(
            """
            INSERT INTO job_description
                (user_id, title, company, raw_text, structured_json, content_hash, source_type, source_url,
                 created_at, updated_at)
            VALUES
                (#{userId}, #{title}, #{company}, #{rawText}, CAST(#{structuredJson,jdbcType=VARCHAR} AS jsonb),
                 #{contentHash}, #{sourceType}, #{sourceUrl}, now(), now())
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(JobDescriptionDO row);

    @Select(
            """
            SELECT id, user_id, title, company, structured_json::text AS structured_json, match_score, created_at
            FROM job_description
            WHERE user_id = #{userId} AND deleted_at IS NULL
            ORDER BY created_at DESC, id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<JobDescriptionDO> list(@Param("userId") long userId, @Param("offset") int offset, @Param("limit") int limit);

    /**
     * 按内容哈希查同用户已有 JD（未删除）；用于去重与复用。
     */
    @Select("SELECT " + COLUMNS
            + " FROM job_description WHERE user_id = #{userId} AND content_hash = #{contentHash}"
            + " AND deleted_at IS NULL ORDER BY id LIMIT 1")
    JobDescriptionDO findByContentHash(@Param("userId") long userId, @Param("contentHash") String contentHash);

    @Select("SELECT COUNT(*) FROM job_description WHERE user_id = #{userId} AND deleted_at IS NULL")
    long count(@Param("userId") long userId);

    @Update(
            """
            UPDATE job_description
            SET match_score = #{matchScore},
                match_report = CAST(#{matchReport,jdbcType=VARCHAR} AS jsonb),
                updated_at = now()
            WHERE id = #{id} AND deleted_at IS NULL
            """)
    int updateMatch(
            @Param("id") long id, @Param("matchScore") BigDecimal matchScore, @Param("matchReport") String matchReport);

    @Update(
            """
            UPDATE job_description
            SET deleted_at = #{deletedAt}, updated_at = now()
            WHERE id = #{id} AND user_id = #{userId} AND deleted_at IS NULL
            """)
    int markDeleted(@Param("userId") long userId, @Param("id") long id, @Param("deletedAt") OffsetDateTime deletedAt);
}
