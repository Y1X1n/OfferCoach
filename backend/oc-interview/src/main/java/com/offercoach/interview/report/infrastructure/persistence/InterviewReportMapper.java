package com.offercoach.interview.report.infrastructure.persistence;

import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * interview_report 表访问；报告生成抢占使用 status + updated_at 条件更新。
 */
@Mapper
public interface InterviewReportMapper {

    String COLUMNS =
            """
            id, session_id, user_id, status, overall_score, radar::text AS radar_json, summary,
            highlights::text AS highlights_json, risks::text AS risks_json,
            improvement_plan::text AS improvement_plan_json,
            question_breakdown::text AS question_breakdown_json,
            attempts, last_error, generated_at, created_at, updated_at
            """;

    @Insert(
            """
            INSERT INTO interview_report (session_id, user_id, status, created_at, updated_at)
            VALUES (#{sessionId}, #{userId}, 'GENERATING', now(), now())
            ON CONFLICT (session_id) DO NOTHING
            """)
    int insertIfAbsent(@Param("sessionId") long sessionId, @Param("userId") long userId);

    @Select("SELECT " + COLUMNS + " FROM interview_report WHERE id = #{id} AND user_id = #{userId}")
    InterviewReportDO findOwned(@Param("userId") long userId, @Param("id") long id);

    @Select("SELECT " + COLUMNS + " FROM interview_report WHERE session_id = #{sessionId}")
    InterviewReportDO findBySession(@Param("sessionId") long sessionId);

    @Select("SELECT " + COLUMNS + " FROM interview_report WHERE id = #{id}")
    InterviewReportDO findById(@Param("id") long id);

    @Select(
            """
            SELECT id, session_id, user_id, status, overall_score, summary, attempts, last_error,
                   generated_at, created_at, updated_at
            FROM interview_report
            WHERE user_id = #{userId}
            ORDER BY created_at DESC, id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<InterviewReportDO> list(@Param("userId") long userId, @Param("offset") int offset, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM interview_report WHERE user_id = #{userId}")
    long count(@Param("userId") long userId);

    @Select(
            """
            SELECT id FROM interview_report
            WHERE status = 'GENERATING' AND updated_at < now() - (#{staleSeconds} * interval '1 second')
            ORDER BY id
            LIMIT #{limit}
            """)
    List<Long> findStaleGenerating(@Param("staleSeconds") int staleSeconds, @Param("limit") int limit);

    @Update(
            """
            UPDATE interview_report
            SET generation_token = #{token}, updated_at = now()
            WHERE id = #{id} AND status = 'GENERATING'
              AND updated_at < now() - (#{staleSeconds} * interval '1 second')
            """)
    int claim(@Param("id") long id, @Param("staleSeconds") int staleSeconds, @Param("token") String token);

    @Update(
            """
            UPDATE interview_report
            SET status = 'READY', overall_score = #{overallScore},
                radar = CAST(#{radarJson,jdbcType=VARCHAR} AS jsonb),
                summary = #{summary},
                highlights = CAST(#{highlightsJson,jdbcType=VARCHAR} AS jsonb),
                risks = CAST(#{risksJson,jdbcType=VARCHAR} AS jsonb),
                improvement_plan = CAST(#{improvementPlanJson,jdbcType=VARCHAR} AS jsonb),
                question_breakdown = CAST(#{questionBreakdownJson,jdbcType=VARCHAR} AS jsonb),
                last_error = NULL, generated_at = now(), updated_at = now(), generation_token = NULL
            WHERE id = #{id} AND status = 'GENERATING' AND generation_token = #{token}
            """)
    int markReady(
            @Param("id") long id,
            @Param("overallScore") BigDecimal overallScore,
            @Param("radarJson") String radarJson,
            @Param("summary") String summary,
            @Param("highlightsJson") String highlightsJson,
            @Param("risksJson") String risksJson,
            @Param("improvementPlanJson") String improvementPlanJson,
            @Param("questionBreakdownJson") String questionBreakdownJson,
            @Param("token") String token);

    @Select(
            """
            UPDATE interview_report
            SET attempts = attempts + 1, last_error = #{lastError}, updated_at = now()
            WHERE id = #{id} AND status = 'GENERATING' AND generation_token = #{token}
            RETURNING attempts
            """)
    Integer incrementAttempts(@Param("id") long id, @Param("lastError") String lastError, @Param("token") String token);

    @Update(
            """
            UPDATE interview_report
            SET status = 'FAILED', last_error = #{lastError}, updated_at = now(), generation_token = NULL
            WHERE id = #{id} AND status = 'GENERATING' AND generation_token = #{token}
            """)
    int markFailed(@Param("id") long id, @Param("lastError") String lastError, @Param("token") String token);

    @Update(
            """
            UPDATE interview_report
            SET status = 'GENERATING', attempts = 0, last_error = NULL, updated_at = now(),
                generation_token = NULL
            WHERE id = #{id} AND user_id = #{userId} AND status = 'FAILED'
            """)
    int resetForRetry(@Param("userId") long userId, @Param("id") long id);
}
