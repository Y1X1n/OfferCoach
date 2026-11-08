package com.offercoach.interview.resume.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * resume 表访问；查询统一排除软删行，按用户读取时附带 user_id。
 */
@Mapper
public interface ResumeMapper {

    String COLUMNS =
            """
            id, user_id, title, source_type, file_key, file_name, mime_type, file_size, sha256,
            page_count, parse_status, parse_error, raw_text, structured_json::text AS structured_json,
            summary, summary_hash, is_default AS default_flag, created_at
            """;

    @Select("SELECT " + COLUMNS + " FROM resume WHERE id = #{id} AND user_id = #{userId} AND deleted_at IS NULL")
    ResumeDO findOwned(@Param("userId") long userId, @Param("id") long id);

    @Select("SELECT " + COLUMNS + " FROM resume WHERE id = #{id} AND deleted_at IS NULL")
    ResumeDO findAny(@Param("id") long id);

    @Select(
            """
            SELECT id, user_id, title, source_type, file_key, file_name, mime_type, file_size, sha256
            FROM resume
            WHERE user_id = #{userId} AND sha256 = #{sha256} AND deleted_at IS NULL
            """)
    ResumeDO findBySha256(@Param("userId") long userId, @Param("sha256") String sha256);

    @Insert(
            """
            INSERT INTO resume
                (user_id, title, source_type, file_key, file_name, mime_type, file_size, sha256,
                 page_count, parse_status, parse_error, raw_text, structured_json, summary, summary_hash,
                 is_default, created_at, updated_at)
            VALUES
                (#{userId}, #{title}, #{sourceType}, #{fileKey}, #{fileName}, #{mimeType}, #{fileSize}, #{sha256},
                 #{pageCount}, #{parseStatus}, #{parseError}, #{rawText},
                 CAST(#{structuredJson,jdbcType=VARCHAR} AS jsonb),
                 #{summary}, #{summaryHash}, #{defaultFlag}, now(), now())
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(ResumeDO row);

    @Select(
            """
            SELECT id, user_id, title, source_type, file_name, mime_type, file_size, sha256, page_count,
                   parse_status, parse_error, summary, is_default AS default_flag, created_at
            FROM resume
            WHERE user_id = #{userId} AND deleted_at IS NULL
            ORDER BY created_at DESC, id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<ResumeDO> list(@Param("userId") long userId, @Param("offset") int offset, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM resume WHERE user_id = #{userId} AND deleted_at IS NULL")
    long count(@Param("userId") long userId);

    @Update(
            """
            UPDATE resume
            SET is_default = false, updated_at = now()
            WHERE user_id = #{userId} AND is_default AND deleted_at IS NULL
            """)
    int clearDefault(@Param("userId") long userId);

    @Update(
            """
            UPDATE resume
            SET is_default = true, updated_at = now()
            WHERE id = #{id} AND user_id = #{userId} AND deleted_at IS NULL
            """)
    int setDefault(@Param("userId") long userId, @Param("id") long id);

    @Update(
            """
            UPDATE resume
            SET parse_status = #{parseStatus}, parse_error = #{parseError}, updated_at = now()
            WHERE id = #{id} AND deleted_at IS NULL
            """)
    int updateParseState(
            @Param("id") long id, @Param("parseStatus") String parseStatus, @Param("parseError") String parseError);

    /**
     * 看门狗：找出长时间停留在 PENDING/PARSING 的简历。
     */
    @Select(
            """
            SELECT id FROM resume
            WHERE parse_status IN ('PENDING', 'PARSING') AND deleted_at IS NULL AND updated_at < #{cutoff}
            ORDER BY updated_at
            LIMIT #{limit}
            """)
    java.util.List<Long> findStaleParsingIds(
            @Param("cutoff") java.time.OffsetDateTime cutoff, @Param("limit") int limit);

    @Update(
            """
            UPDATE resume
            SET parse_status = 'READY',
                parse_error = NULL,
                raw_text = #{rawText},
                structured_json = CAST(#{structuredJson,jdbcType=VARCHAR} AS jsonb),
                summary = #{summary},
                summary_hash = #{summaryHash},
                page_count = #{pageCount},
                updated_at = now()
            WHERE id = #{id} AND deleted_at IS NULL
            """)
    int markReady(
            @Param("id") long id,
            @Param("rawText") String rawText,
            @Param("structuredJson") String structuredJson,
            @Param("summary") String summary,
            @Param("summaryHash") String summaryHash,
            @Param("pageCount") int pageCount);

    @Update(
            """
            UPDATE resume
            SET parse_status = 'FAILED', parse_error = #{parseError}, updated_at = now()
            WHERE id = #{id} AND deleted_at IS NULL
            """)
    int markFailed(@Param("id") long id, @Param("parseError") String parseError);

    @Update(
            """
            UPDATE resume
            SET parse_status = 'FAILED', parse_error = #{parseError}, updated_at = now()
            WHERE id = #{id} AND deleted_at IS NULL AND parse_status IN ('PENDING', 'PARSING')
            """)
    int failIfParsing(@Param("id") long id, @Param("parseError") String parseError);

    @Update(
            """
            UPDATE resume
            SET title = #{title}, updated_at = now()
            WHERE id = #{id} AND user_id = #{userId} AND deleted_at IS NULL
            """)
    int updateTitle(@Param("userId") long userId, @Param("id") long id, @Param("title") String title);

    @Update(
            """
            UPDATE resume
            SET structured_json = CAST(#{structuredJson,jdbcType=VARCHAR} AS jsonb),
                summary = #{summary},
                summary_hash = #{summaryHash},
                updated_at = now()
            WHERE id = #{id} AND deleted_at IS NULL
            """)
    int updateStructured(
            @Param("id") long id,
            @Param("structuredJson") String structuredJson,
            @Param("summary") String summary,
            @Param("summaryHash") String summaryHash);

    @Update(
            """
            UPDATE resume
            SET is_default = #{isDefault}, updated_at = now()
            WHERE id = #{id} AND user_id = #{userId} AND deleted_at IS NULL
            """)
    int updateDefault(@Param("userId") long userId, @Param("id") long id, @Param("isDefault") boolean isDefault);

    @Update(
            """
            UPDATE resume
            SET deleted_at = #{deletedAt}, is_default = false, updated_at = now()
            WHERE id = #{id} AND user_id = #{userId} AND deleted_at IS NULL
            """)
    int markDeleted(@Param("userId") long userId, @Param("id") long id, @Param("deletedAt") OffsetDateTime deletedAt);

    @Select(
            """
            SELECT COUNT(*) FROM interview_session
            WHERE user_id = #{userId} AND resume_id = #{resumeId}
              AND status IN ('CREATED', 'PREPARING', 'READY', 'IN_PROGRESS', 'PAUSED')
            """)
    long countActiveSessions(@Param("userId") long userId, @Param("resumeId") long resumeId);
}
