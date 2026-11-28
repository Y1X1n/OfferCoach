package com.offercoach.interview.feedback.infrastructure.persistence;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * answer_feedback 表访问；request_id 唯一保证同一次评分幂等落库。
 */
@Mapper
public interface AnswerFeedbackMapper {

    String COLUMNS =
            """
            id, answer_id, version, request_id, session_id, user_id, overall_score,
            dimensions::text AS dimensions_json, strengths::text AS strengths_json,
            improvements::text AS improvements_json, evidence::text AS evidence_json,
            rewritten_answer, model, provider, degraded, created_at
            """;

    @Select("SELECT " + COLUMNS
            + " FROM answer_feedback WHERE answer_id = #{answerId} AND user_id = #{userId}"
            + " ORDER BY version DESC LIMIT 1")
    AnswerFeedbackDO findLatestByAnswer(@Param("userId") long userId, @Param("answerId") long answerId);

    @Select("SELECT " + COLUMNS + " FROM answer_feedback WHERE request_id = #{requestId}")
    AnswerFeedbackDO findByRequestId(@Param("requestId") String requestId);

    @Insert(
            """
            INSERT INTO answer_feedback
                (answer_id, version, request_id, session_id, user_id, overall_score, dimensions,
                 strengths, improvements, evidence, rewritten_answer, model, provider, degraded, created_at)
            VALUES
                (#{answerId}, #{version}, #{requestId}, #{sessionId}, #{userId}, #{overallScore},
                 CAST(#{dimensionsJson,jdbcType=VARCHAR} AS jsonb),
                 CAST(#{strengthsJson,jdbcType=VARCHAR} AS jsonb),
                 CAST(#{improvementsJson,jdbcType=VARCHAR} AS jsonb),
                 CAST(#{evidenceJson,jdbcType=VARCHAR} AS jsonb),
                 #{rewrittenAnswer}, #{model}, #{provider}, #{degraded}, now())
            ON CONFLICT DO NOTHING
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertIfAbsent(AnswerFeedbackDO row);

    @Select(
            """
            SELECT id, answer_id, version, request_id, session_id, user_id, overall_score,
                   dimensions::text AS dimensions_json, strengths::text AS strengths_json,
                   improvements::text AS improvements_json, evidence::text AS evidence_json,
                   rewritten_answer, model, provider, degraded, created_at
            FROM answer_feedback
            WHERE session_id = #{sessionId}
            ORDER BY id
            """)
    List<AnswerFeedbackDO> listBySession(@Param("sessionId") long sessionId);

    /**
     * 报告详情的批量取数：同一回答只取 version 最大的一版，与
     * {@link #findLatestByAnswer} 的 {@code ORDER BY version DESC LIMIT 1} 语义一致
     * （version 相同时按 id 取最新，避免同版本脏数据导致结果不确定）。
     */
    @Select(
            """
            SELECT DISTINCT ON (answer_id) """
                    + COLUMNS
                    + """
            FROM answer_feedback
            WHERE user_id = #{userId} AND session_id = #{sessionId}
            ORDER BY answer_id, version DESC, id DESC
            """)
    List<AnswerFeedbackDO> listLatestBySession(@Param("userId") long userId, @Param("sessionId") long sessionId);
}
