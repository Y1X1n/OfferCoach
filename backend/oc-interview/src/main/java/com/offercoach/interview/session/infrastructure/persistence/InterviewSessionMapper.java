package com.offercoach.interview.session.infrastructure.persistence;

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
 * interview_session 表访问；状态迁移一律带原状态条件，避免并发覆盖。
 */
@Mapper
public interface InterviewSessionMapper {

    String COLUMNS =
            """
            id, user_id, resume_id, jd_id, status, config::text AS config_json, question_count,
            answered_count, current_seq, total_score, failure_reason, idempotency_key, started_at,
            finished_at, last_active_at, expires_at, created_at
            """;

    @Select("SELECT " + COLUMNS + " FROM interview_session WHERE id = #{id} AND user_id = #{userId}")
    InterviewSessionDO findOwned(@Param("userId") long userId, @Param("id") long id);

    @Select("SELECT " + COLUMNS + " FROM interview_session WHERE id = #{id}")
    InterviewSessionDO findAny(@Param("id") long id);

    @Select("SELECT " + COLUMNS + " FROM interview_session WHERE user_id = #{userId} AND idempotency_key = #{key}")
    InterviewSessionDO findByUserAndKey(@Param("userId") long userId, @Param("key") String key);

    @Insert(
            """
            INSERT INTO interview_session
                (user_id, resume_id, jd_id, status, config, question_count, answered_count, current_seq,
                 idempotency_key, last_active_at, created_at, updated_at)
            VALUES
                (#{userId}, #{resumeId}, #{jdId}, #{status}, CAST(#{configJson,jdbcType=VARCHAR} AS jsonb),
                 #{questionCount}, 0, 1, #{idempotencyKey}, now(), now(), now())
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(InterviewSessionDO row);

    @Select(
            """
            SELECT COUNT(*) FROM interview_session
            WHERE user_id = #{userId}
              AND status IN ('CREATED', 'PREPARING', 'READY', 'IN_PROGRESS', 'PAUSED')
            """)
    long countActive(@Param("userId") long userId);

    @Select(
            """
            SELECT id, user_id, resume_id, jd_id, status, config::text AS config_json, question_count,
                   answered_count, current_seq, total_score, failure_reason, idempotency_key,
                   started_at, finished_at, last_active_at, expires_at, created_at
            FROM interview_session
            WHERE user_id = #{userId}
            ORDER BY created_at DESC, id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<InterviewSessionDO> list(@Param("userId") long userId, @Param("offset") int offset, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM interview_session WHERE user_id = #{userId}")
    long count(@Param("userId") long userId);

    @Update(
            """
            UPDATE interview_session
            SET status = 'PREPARING', started_at = now(), updated_at = now(), last_active_at = now()
            WHERE id = #{id} AND status = 'CREATED'
            """)
    int markPreparing(@Param("id") long id);

    @Update(
            """
            UPDATE interview_session
            SET status = 'READY', updated_at = now()
            WHERE id = #{id} AND status = 'PREPARING'
            """)
    int markReady(@Param("id") long id);

    @Update(
            """
            UPDATE interview_session
            SET status = 'FAILED', failure_reason = #{failureReason}, updated_at = now()
            WHERE id = #{id} AND status = 'PREPARING'
            """)
    int markGenerationFailed(@Param("id") long id, @Param("failureReason") String failureReason);

    @Update(
            """
            UPDATE interview_session
            SET status = 'IN_PROGRESS', updated_at = now(), last_active_at = now()
            WHERE id = #{id} AND status = 'READY'
            """)
    int markInProgress(@Param("id") long id);

    @Update(
            """
            UPDATE interview_session
            SET status = 'PAUSED', updated_at = now(), last_active_at = now()
            WHERE id = #{id} AND status = 'IN_PROGRESS'
            """)
    int pause(@Param("id") long id);

    @Update(
            """
            UPDATE interview_session
            SET status = 'IN_PROGRESS', updated_at = now(), last_active_at = now()
            WHERE id = #{id} AND status = 'PAUSED'
            """)
    int resume(@Param("id") long id);

    @Update(
            """
            UPDATE interview_session
            SET status = 'ABANDONED', updated_at = now(), last_active_at = now()
            WHERE id = #{id}
              AND status IN ('CREATED', 'PREPARING', 'READY', 'IN_PROGRESS', 'PAUSED')
            """)
    int abandon(@Param("id") long id);

    @Update(
            """
            UPDATE interview_session
            SET status = 'REPORT_GENERATING', finished_at = now(), updated_at = now(), last_active_at = now()
            WHERE id = #{id} AND status IN ('IN_PROGRESS', 'PAUSED')
            """)
    int finish(@Param("id") long id);

    @Update(
            """
            UPDATE interview_session
            SET status = 'COMPLETED', total_score = #{totalScore}, updated_at = now()
            WHERE id = #{id} AND status = 'REPORT_GENERATING'
            """)
    int complete(@Param("id") long id, @Param("totalScore") BigDecimal totalScore);

    @Update(
            """
            UPDATE interview_session
            SET status = 'REPORT_FAILED', updated_at = now()
            WHERE id = #{id} AND status = 'REPORT_GENERATING'
            """)
    int markReportFailed(@Param("id") long id);

    @Update(
            """
            UPDATE interview_session
            SET status = 'REPORT_GENERATING', updated_at = now()
            WHERE id = #{id} AND status = 'REPORT_FAILED'
            """)
    int markReportGenerating(@Param("id") long id);

    @Update(
            """
            UPDATE interview_session
            SET last_active_at = now(), updated_at = now()
            WHERE id = #{id} AND status IN ('IN_PROGRESS', 'PAUSED')
            """)
    int touch(@Param("id") long id);

    @Update(
            """
            UPDATE interview_session
            SET answered_count = answered_count + 1,
                current_seq = LEAST(current_seq + 1, question_count + 1),
                last_active_at = now(),
                updated_at = now()
            WHERE id = #{id} AND status = 'IN_PROGRESS'
            """)
    int advanceAfterAnswer(@Param("id") long id);

    @Update(
            """
            UPDATE interview_session
            SET current_seq = LEAST(current_seq + 1, question_count + 1),
                last_active_at = now(),
                updated_at = now()
            WHERE id = #{id} AND status = 'IN_PROGRESS'
            """)
    int advanceAfterSkip(@Param("id") long id);

    @Select(
            """
            SELECT id, user_id, resume_id, jd_id, status, config::text AS config_json, question_count,
                   answered_count, current_seq, total_score, failure_reason, idempotency_key,
                   started_at, finished_at, last_active_at, expires_at, created_at
            FROM interview_session
            WHERE status IN ('IN_PROGRESS', 'PAUSED') AND last_active_at < #{cutoff}
            ORDER BY last_active_at
            LIMIT #{limit}
            """)
    List<InterviewSessionDO> findIdle(@Param("cutoff") OffsetDateTime cutoff, @Param("limit") int limit);

    /**
     * 看门狗：找出长时间停留在 PREPARING 的会话，供定时任务置失败并退款。
     */
    @Select(
            """
            SELECT id, user_id, status FROM interview_session
            WHERE status = 'PREPARING' AND updated_at < #{cutoff}
            ORDER BY updated_at
            LIMIT #{limit}
            """)
    List<InterviewSessionDO> findStalePreparing(@Param("cutoff") OffsetDateTime cutoff, @Param("limit") int limit);

    @Update(
            """
            UPDATE interview_session
            SET status = 'ABANDONED', updated_at = now()
            WHERE id = #{id}
              AND status IN ('IN_PROGRESS', 'PAUSED')
              AND last_active_at < #{cutoff}
            """)
    int markIdleAbandoned(@Param("id") long id, @Param("cutoff") OffsetDateTime cutoff);
}
