package com.offercoach.identity.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDate;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserQuotaMapper extends BaseMapper<UserQuotaDO> {

    /**
     * 行锁读取；同一用户的扣减 / 返还由此串行化。
     */
    @Select("SELECT * FROM user_quota WHERE user_id = #{userId} FOR UPDATE")
    UserQuotaDO selectForUpdate(@Param("userId") long userId);

    /**
     * 幂等初始化；并发注册时由主键冲突兜底为 no-op，避免事务内异常。
     */
    @Insert(
            """
            INSERT INTO user_quota
                (user_id, plan_code, interview_credits, resume_credits,
                 period_start, period_end, daily_session_date, daily_session_count)
            VALUES
                (#{userId}, #{planCode}, #{interviewCredits}, #{resumeCredits},
                 #{periodStart}, #{periodEnd}, #{dailyDate}, 0)
            ON CONFLICT (user_id) DO NOTHING
            """)
    int insertIfAbsent(
            @Param("userId") long userId,
            @Param("planCode") String planCode,
            @Param("interviewCredits") int interviewCredits,
            @Param("resumeCredits") int resumeCredits,
            @Param("periodStart") LocalDate periodStart,
            @Param("periodEnd") LocalDate periodEnd,
            @Param("dailyDate") LocalDate dailyDate);

    @Update(
            """
            UPDATE user_quota
            SET interview_credits = interview_credits - #{amount},
                version = version + 1,
                updated_at = now()
            WHERE user_id = #{userId} AND interview_credits >= #{amount}
            """)
    int decreaseInterview(@Param("userId") long userId, @Param("amount") int amount);

    @Update(
            """
            UPDATE user_quota
            SET resume_credits = resume_credits - #{amount},
                version = version + 1,
                updated_at = now()
            WHERE user_id = #{userId} AND resume_credits >= #{amount}
            """)
    int decreaseResume(@Param("userId") long userId, @Param("amount") int amount);

    @Update(
            """
            UPDATE user_quota
            SET interview_credits = interview_credits + #{amount},
                version = version + 1,
                updated_at = now()
            WHERE user_id = #{userId}
            """)
    int increaseInterview(@Param("userId") long userId, @Param("amount") int amount);

    @Update(
            """
            UPDATE user_quota
            SET resume_credits = resume_credits + #{amount},
                version = version + 1,
                updated_at = now()
            WHERE user_id = #{userId}
            """)
    int increaseResume(@Param("userId") long userId, @Param("amount") int amount);

    /**
     * 跨天重置每日会话计数；已重置过则影响 0 行。
     */
    @Update(
            """
            UPDATE user_quota
            SET daily_session_date = #{today},
                daily_session_count = 0,
                version = version + 1,
                updated_at = now()
            WHERE user_id = #{userId}
              AND (daily_session_date IS NULL OR daily_session_date <> #{today})
            """)
    int resetDailyIfStale(@Param("userId") long userId, @Param("today") LocalDate today);

    /**
     * 周期到期续发：降级为 FREE 并按运行时策略续发月度额度，原子重置每日计数。
     * 已续发或未到期时影响 0 行。
     */
    @Update(
            """
            UPDATE user_quota
            SET plan_code = #{planCode},
                interview_credits = interview_credits + #{interviewAdd},
                resume_credits = resume_credits + #{resumeAdd},
                period_start = #{today},
                period_end = #{nextPeriodEnd},
                daily_session_date = #{today},
                daily_session_count = 0,
                version = version + 1,
                updated_at = now()
            WHERE user_id = #{userId}
              AND (period_end IS NULL OR period_end <= #{today})
            """)
    int renewExpired(
            @Param("userId") long userId,
            @Param("planCode") String planCode,
            @Param("interviewAdd") int interviewAdd,
            @Param("resumeAdd") int resumeAdd,
            @Param("today") LocalDate today,
            @Param("nextPeriodEnd") LocalDate nextPeriodEnd);

    /**
     * 原子占用一次每日会话名额：跨天自动重置为 1，同日未达上限则 +1。
     * 返回 0 表示已达上限（或行不存在）。
     */
    @Update(
            """
            UPDATE user_quota
            SET daily_session_date = #{today},
                daily_session_count = CASE
                    WHEN daily_session_date = #{today} THEN daily_session_count + 1
                    ELSE 1
                END,
                version = version + 1,
                updated_at = now()
            WHERE user_id = #{userId}
              AND (daily_session_date IS DISTINCT FROM #{today} OR daily_session_count < #{cap})
            """)
    int incrementDailyIfUnderCap(@Param("userId") long userId, @Param("today") LocalDate today, @Param("cap") int cap);
}
