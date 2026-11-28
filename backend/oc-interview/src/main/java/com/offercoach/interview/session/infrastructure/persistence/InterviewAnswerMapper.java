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
            submitted_at, created_at, audio_key, audio_duration_ms, asr_status, asr_confidence
            """;

    @Insert(
            """
            INSERT INTO interview_answer
                (session_id, question_id, user_id, input_mode, answer_text, duration_ms, status,
                 submitted_at, created_at, audio_key, audio_duration_ms, asr_status, asr_confidence)
            VALUES
                (#{sessionId}, #{questionId}, #{userId}, #{inputMode}, #{answerText}, #{durationMs},
                 #{status}, now(), now(), #{audioKey}, #{audioDurationMs}, #{asrStatus}, #{asrConfidence})
            ON CONFLICT (question_id) DO NOTHING
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

    @Select("SELECT " + COLUMNS + " FROM interview_answer WHERE session_id = #{sessionId}"
            + " ORDER BY submitted_at, id")
    List<InterviewAnswerDO> listBySession(@Param("sessionId") long sessionId);

    /**
     * 报告详情的批量取数：归属条件与 {@link #findOwned} 一致（user + session）。
     */
    @Select("SELECT " + COLUMNS
            + " FROM interview_answer WHERE user_id = #{userId} AND session_id = #{sessionId}"
            + " ORDER BY submitted_at, id")
    List<InterviewAnswerDO> listOwnedBySession(@Param("userId") long userId, @Param("sessionId") long sessionId);

    /**
     * 评分完成：不覆盖 REJECTED（内容不合规）的回答。
     *
     * <p>从 FAILED 迁回 SCORED 是合法的：看门狗把超时回答标 FAILED 后，评分任务若最终成功，
     * 前端应看到分数而不是「评分失败」。但 REJECTED 是审核结论，不能被评分结果复活。</p>
     */
    @Update("UPDATE interview_answer SET status = 'SCORED' WHERE id = #{answerId} AND status <> 'REJECTED'")
    int markScored(@Param("answerId") long answerId);

    /**
     * 看门狗：长时间停留在 SCORING 的回答标记失败，前端可重试评分。
     */
    @Update(
            """
            UPDATE interview_answer
            SET status = 'FAILED'
            WHERE id IN (
                SELECT id FROM interview_answer
                WHERE status = 'SCORING' AND submitted_at < #{cutoff}
                LIMIT 500
            )
            """)
    int failStaleScoring(@Param("cutoff") java.time.OffsetDateTime cutoff);
}
