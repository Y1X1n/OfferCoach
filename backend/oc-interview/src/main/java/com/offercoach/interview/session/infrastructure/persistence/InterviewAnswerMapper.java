package com.offercoach.interview.session.infrastructure.persistence;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * interview_answer 表访问；question_id 唯一保证每题至多一条回答。
 */
@Mapper
public interface InterviewAnswerMapper {

    String COLUMNS =
            """
            id, session_id, question_id, user_id, input_mode, answer_text, duration_ms, status,
            submitted_at, created_at
            """;

    @Insert(
            """
            INSERT INTO interview_answer
                (session_id, question_id, user_id, input_mode, answer_text, duration_ms, status,
                 submitted_at, created_at)
            VALUES
                (#{sessionId}, #{questionId}, #{userId}, #{inputMode}, #{answerText}, #{durationMs},
                 #{status}, now(), now())
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(InterviewAnswerDO row);

    @Select("SELECT " + COLUMNS
            + " FROM interview_answer WHERE id = #{answerId}"
            + " AND session_id = #{sessionId} AND user_id = #{userId}")
    InterviewAnswerDO findOwned(
            @Param("userId") long userId, @Param("sessionId") long sessionId, @Param("answerId") long answerId);

    @Select("SELECT " + COLUMNS + " FROM interview_answer WHERE question_id = #{questionId}")
    InterviewAnswerDO findByQuestionId(@Param("questionId") long questionId);

    @Select("SELECT " + COLUMNS + " FROM interview_answer WHERE id = #{answerId} AND user_id = #{userId}")
    InterviewAnswerDO findOwnedByAnswer(@Param("userId") long userId, @Param("answerId") long answerId);

    @Select("SELECT COUNT(*) FROM interview_answer WHERE session_id = #{sessionId}")
    long countBySession(@Param("sessionId") long sessionId);

    @Select(
            """
            SELECT id, session_id, question_id, user_id, input_mode, answer_text, duration_ms, status,
                   submitted_at, created_at
            FROM interview_answer
            WHERE session_id = #{sessionId}
            ORDER BY submitted_at, id
            """)
    List<InterviewAnswerDO> listBySession(@Param("sessionId") long sessionId);

    @Update("UPDATE interview_answer SET status = 'SCORED' WHERE id = #{answerId}")
    int markScored(@Param("answerId") long answerId);

    /**
     * 看门狗：长时间停留在 SCORING 的回答标记失败，前端可重试评分。
     */
    @Update(
            """
            UPDATE interview_answer
            SET status = 'FAILED'
            WHERE status = 'SCORING' AND submitted_at < #{cutoff}
            """)
    int failStaleScoring(@Param("cutoff") java.time.OffsetDateTime cutoff);
}
