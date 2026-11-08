package com.offercoach.interview.session.infrastructure.persistence;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * interview_question 表访问；reference_points / context_json / retrieval_json 以文本出入 JSONB。
 */
@Mapper
public interface InterviewQuestionMapper {

    String COLUMNS =
            """
            id, session_id, seq, dimension, difficulty, question_text, source, bank_question_id,
            parent_question_id, reference_points::text AS reference_points_json,
            context_json::text AS context_json, why_this_question,
            retrieval_json::text AS retrieval_json, status, asked_at, created_at
            """;

    @Insert(
            """
            INSERT INTO interview_question
                (session_id, seq, dimension, difficulty, question_text, source, bank_question_id,
                 parent_question_id, reference_points, context_json, why_this_question, retrieval_json,
                 status, created_at)
            VALUES
                (#{sessionId}, #{seq}, #{dimension}, #{difficulty}, #{questionText}, #{source},
                 #{bankQuestionId}, #{parentQuestionId}, CAST(#{referencePointsJson,jdbcType=VARCHAR} AS jsonb),
                 CAST(#{contextJson,jdbcType=VARCHAR} AS jsonb), #{whyThisQuestion},
                 CAST(#{retrievalJson,jdbcType=VARCHAR} AS jsonb), 'PENDING', now())
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(InterviewQuestionDO row);

    @Select("SELECT " + COLUMNS + " FROM interview_question WHERE session_id = #{sessionId} ORDER BY seq")
    List<InterviewQuestionDO> listBySession(@Param("sessionId") long sessionId);

    @Select("SELECT " + COLUMNS
            + " FROM interview_question WHERE session_id = #{sessionId}"
            + " AND (seq <= #{currentSeq} OR status IN ('ANSWERED', 'SKIPPED')) ORDER BY seq")
    List<InterviewQuestionDO> listVisible(@Param("sessionId") long sessionId, @Param("currentSeq") int currentSeq);

    @Select("SELECT " + COLUMNS + " FROM interview_question WHERE id = #{questionId} AND session_id = #{sessionId}")
    InterviewQuestionDO findOwned(@Param("sessionId") long sessionId, @Param("questionId") long questionId);

    @Select("SELECT COUNT(*) FROM interview_question WHERE session_id = #{sessionId}")
    long countBySession(@Param("sessionId") long sessionId);

    @Select("SELECT COUNT(*) FROM interview_question WHERE session_id = #{sessionId} AND status = #{status}")
    long countByStatus(@Param("sessionId") long sessionId, @Param("status") String status);

    @Update(
            """
            UPDATE interview_question
            SET status = 'ASKED', asked_at = now()
            WHERE session_id = #{sessionId} AND seq = #{seq} AND status = 'PENDING'
            """)
    int markAsked(@Param("sessionId") long sessionId, @Param("seq") int seq);

    @Update(
            """
            UPDATE interview_question
            SET status = 'ANSWERED'
            WHERE id = #{questionId} AND status IN ('PENDING', 'ASKED')
            """)
    int markAnswered(@Param("questionId") long questionId);

    @Update(
            """
            UPDATE interview_question
            SET status = 'SKIPPED'
            WHERE id = #{questionId} AND status IN ('PENDING', 'ASKED')
            """)
    int markSkipped(@Param("questionId") long questionId);
}
