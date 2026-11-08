package com.offercoach.identity.application.settings;

import com.offercoach.common.error.BizException;
import com.offercoach.common.error.ErrorCode;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 单行运行时策略：注册开关、每日额度、邀请奖励与注册风控阈值；缓存 30 秒。
 */
@Service
public class PlatformSettingsService {

    private static final long CACHE_TTL_MILLIS = 30_000L;
    private static final String SELECT_SQL =
            """
            SELECT registration_open, daily_interview_limit, daily_resume_limit,
                   free_monthly_interviews, free_monthly_resumes,
                   invite_reward_inviter_interview, invite_reward_invitee_resume,
                   invite_daily_cap, invite_total_cap,
                   registration_ip_daily_cap, registration_device_daily_cap,
                   updated_by, updated_at
            FROM platform_settings WHERE id = 1
            """;
    private static final String UPDATE_SQL =
            """
            UPDATE platform_settings
            SET registration_open = ?, daily_interview_limit = ?, daily_resume_limit = ?,
                free_monthly_interviews = ?, free_monthly_resumes = ?,
                invite_reward_inviter_interview = ?, invite_reward_invitee_resume = ?,
                invite_daily_cap = ?, invite_total_cap = ?,
                registration_ip_daily_cap = ?, registration_device_daily_cap = ?,
                updated_by = ?, updated_at = now()
            WHERE id = 1
            """;

    private final JdbcTemplate jdbcTemplate;
    private volatile CacheEntry cache;

    public PlatformSettingsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Snapshot snapshot() {
        CacheEntry cached = cache;
        long now = System.currentTimeMillis();
        if (cached != null && cached.expiresAtMillis() > now) {
            return cached.value();
        }
        Snapshot value = read();
        cache = new CacheEntry(value, now + CACHE_TTL_MILLIS);
        return value;
    }

    @Transactional
    public Snapshot update(long actorId, Command command) {
        Snapshot normalized = new Snapshot(
                command.registrationOpen(),
                requireRange("dailyInterviewLimit", command.dailyInterviewLimit(), 0, 10000),
                requireRange("dailyResumeLimit", command.dailyResumeLimit(), 0, 10000),
                requireRange("freeMonthlyInterviews", command.freeMonthlyInterviews(), 0, 1000),
                requireRange("freeMonthlyResumes", command.freeMonthlyResumes(), 0, 1000),
                requireRange("inviteRewardInviterInterview", command.inviteRewardInviterInterview(), 0, 100),
                requireRange("inviteRewardInviteeResume", command.inviteRewardInviteeResume(), 0, 100),
                requireRange("inviteDailyCap", command.inviteDailyCap(), 0, 1000),
                requireRange("inviteTotalCap", command.inviteTotalCap(), 0, 10000),
                requireRange("registrationIpDailyCap", command.registrationIpDailyCap(), 0, 10000),
                requireRange("registrationDeviceDailyCap", command.registrationDeviceDailyCap(), 0, 1000),
                actorId,
                null);
        jdbcTemplate.update(
                UPDATE_SQL,
                normalized.registrationOpen(),
                normalized.dailyInterviewLimit(),
                normalized.dailyResumeLimit(),
                normalized.freeMonthlyInterviews(),
                normalized.freeMonthlyResumes(),
                normalized.inviteRewardInviterInterview(),
                normalized.inviteRewardInviteeResume(),
                normalized.inviteDailyCap(),
                normalized.inviteTotalCap(),
                normalized.registrationIpDailyCap(),
                normalized.registrationDeviceDailyCap(),
                actorId);
        cache = null;
        return snapshot();
    }

    public void invalidate() {
        cache = null;
    }

    private Snapshot read() {
        try {
            var rows = jdbcTemplate.query(
                    SELECT_SQL,
                    (resultSet, index) -> new Snapshot(
                            resultSet.getBoolean("registration_open"),
                            resultSet.getInt("daily_interview_limit"),
                            resultSet.getInt("daily_resume_limit"),
                            resultSet.getInt("free_monthly_interviews"),
                            resultSet.getInt("free_monthly_resumes"),
                            resultSet.getInt("invite_reward_inviter_interview"),
                            resultSet.getInt("invite_reward_invitee_resume"),
                            resultSet.getInt("invite_daily_cap"),
                            resultSet.getInt("invite_total_cap"),
                            resultSet.getInt("registration_ip_daily_cap"),
                            resultSet.getInt("registration_device_daily_cap"),
                            resultSet.getObject("updated_by", Long.class),
                            resultSet.getObject("updated_at", OffsetDateTime.class)));
            if (!rows.isEmpty()) {
                return rows.getFirst();
            }
        } catch (RuntimeException exception) {
            // 表缺失或数据库抖动时回退安全默认值，不阻断登录与业务。
        }
        return new Snapshot(true, 3, 3, 3, 3, 1, 1, 3, 20, 20, 3, null, null);
    }

    private static int requireRange(String field, int value, int min, int max) {
        if (value < min || value > max) {
            throw new BizException(
                    ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of(field, "取值范围 " + min + "-" + max)));
        }
        return value;
    }

    public record Snapshot(
            boolean registrationOpen,
            int dailyInterviewLimit,
            int dailyResumeLimit,
            int freeMonthlyInterviews,
            int freeMonthlyResumes,
            int inviteRewardInviterInterview,
            int inviteRewardInviteeResume,
            int inviteDailyCap,
            int inviteTotalCap,
            int registrationIpDailyCap,
            int registrationDeviceDailyCap,
            Long updatedBy,
            OffsetDateTime updatedAt) {}

    public record Command(
            boolean registrationOpen,
            int dailyInterviewLimit,
            int dailyResumeLimit,
            int freeMonthlyInterviews,
            int freeMonthlyResumes,
            int inviteRewardInviterInterview,
            int inviteRewardInviteeResume,
            int inviteDailyCap,
            int inviteTotalCap,
            int registrationIpDailyCap,
            int registrationDeviceDailyCap) {}

    private record CacheEntry(Snapshot value, long expiresAtMillis) {}
}
