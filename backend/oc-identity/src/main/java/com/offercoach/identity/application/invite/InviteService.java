package com.offercoach.identity.application.invite;

import com.offercoach.common.error.BizException;
import com.offercoach.common.error.ErrorCode;
import com.offercoach.identity.application.quota.QuotaService;
import com.offercoach.identity.application.settings.PlatformSettingsService;
import com.offercoach.identity.domain.quota.QuotaType;
import java.security.SecureRandom;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 邀请码与推荐奖励：注册时可选绑定邀请人，奖励受每日/累计上限约束，同设备不奖励。
 */
@Service
public class InviteService {

    private static final Logger log = LoggerFactory.getLogger(InviteService.class);

    private static final int CODE_LENGTH = 10;
    private static final int MAX_GENERATE_ATTEMPTS = 5;
    private static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String SHANGHAI_TODAY =
            "(created_at AT TIME ZONE 'Asia/Shanghai')::date = (now() AT TIME ZONE 'Asia/Shanghai')::date";

    private final JdbcTemplate jdbcTemplate;
    private final PlatformSettingsService settingsService;
    private final QuotaService quotaService;
    private final SecureRandom random = new SecureRandom();

    public InviteService(
            JdbcTemplate jdbcTemplate, PlatformSettingsService settingsService, QuotaService quotaService) {
        this.jdbcTemplate = jdbcTemplate;
        this.settingsService = settingsService;
        this.quotaService = quotaService;
    }

    /**
     * 注册落库后调用：生成个人邀请码、记录注册指纹、按需绑定邀请人并发放小额度奖励。
     * 邀请码无效时抛字段错误，让用户修正后重试。
     */
    @Transactional
    public void onRegistered(long userId, String inviteCode, String ip, String deviceHash) {
        jdbcTemplate.update(
                "UPDATE app_user SET signup_ip = ?, signup_device_hash = ? WHERE id = ?", ip, deviceHash, userId);
        ensureInviteCode(userId);
        if (inviteCode == null || inviteCode.isBlank()) {
            return;
        }
        Inviter inviter = findInviter(inviteCode.trim());
        if (inviter == null) {
            throw fieldError("inviteCode", "邀请码无效");
        }
        if (inviter.id() == userId) {
            return;
        }
        if (deviceHash != null && deviceHash.equals(inviter.deviceHash())) {
            log.info("邀请人与被邀请人设备相同，仅建立推荐关系不发奖励：inviterId={}", inviter.id());
            jdbcTemplate.update("UPDATE app_user SET referred_by = ? WHERE id = ?", inviter.id(), userId);
            return;
        }

        PlatformSettingsService.Snapshot settings = settingsService.snapshot();
        long todayCount = countToday(inviter.id());
        long totalCount = countTotal(inviter.id());
        boolean rewarded = settings.inviteDailyCap() > 0
                && settings.inviteTotalCap() > 0
                && todayCount < settings.inviteDailyCap()
                && totalCount < settings.inviteTotalCap();
        int inviterReward = rewarded ? settings.inviteRewardInviterInterview() : 0;
        int inviteeReward = rewarded ? settings.inviteRewardInviteeResume() : 0;

        jdbcTemplate.update("UPDATE app_user SET referred_by = ? WHERE id = ?", inviter.id(), userId);
        int inserted = jdbcTemplate.update(
                """
                INSERT INTO invite_reward
                    (inviter_id, invitee_id, inviter_interview_credits, invitee_resume_credits)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (invitee_id) DO NOTHING
                """,
                inviter.id(),
                userId,
                inviterReward,
                inviteeReward);
        if (inserted == 0) {
            return;
        }
        if (inviterReward > 0) {
            quotaService.grant(
                    inviter.id(),
                    QuotaType.INTERVIEW,
                    inviterReward,
                    "INVITE_REWARD",
                    "USER",
                    userId,
                    "invite-reward:inviter:" + userId);
        }
        if (inviteeReward > 0) {
            quotaService.grant(
                    userId,
                    QuotaType.RESUME,
                    inviteeReward,
                    "INVITE_REWARD",
                    "USER",
                    inviter.id(),
                    "invite-reward:invitee:" + userId);
        }
    }

    /**
     * 当前用户的邀请名片：个人码、已邀请人数、累计奖励与剩余名额。
     */
    @Transactional
    public InviteProfile profile(long userId) {
        String code = ensureInviteCode(userId);
        PlatformSettingsService.Snapshot settings = settingsService.snapshot();
        InviteStats stats = jdbcTemplate.queryForObject(
                """
                SELECT count(*) AS invited,
                       coalesce(sum(inviter_interview_credits), 0) AS interview_reward,
                       coalesce(sum(invitee_resume_credits), 0) AS resume_reward
                FROM invite_reward WHERE inviter_id = ?
                """,
                (resultSet, index) -> new InviteStats(
                        resultSet.getLong("invited"),
                        resultSet.getLong("interview_reward"),
                        resultSet.getLong("resume_reward")),
                userId);
        long todayCount = countToday(userId);
        long invited = stats == null ? 0 : stats.invited();
        long dailyCap = settings.inviteDailyCap();
        long totalCap = settings.inviteTotalCap();
        return new InviteProfile(
                code,
                invited,
                stats == null ? 0 : stats.interviewReward(),
                stats == null ? 0 : stats.resumeReward(),
                settings.inviteRewardInviterInterview(),
                settings.inviteRewardInviteeResume(),
                (int) dailyCap,
                (int) totalCap,
                Math.max(0, dailyCap - todayCount),
                Math.max(0, totalCap - invited));
    }

    private Inviter findInviter(String inviteCode) {
        var rows = jdbcTemplate.query(
                """
                SELECT id, signup_device_hash FROM app_user
                WHERE upper(invite_code) = upper(?) AND deleted_at IS NULL AND status = 'ACTIVE'
                """,
                (resultSet, index) -> new Inviter(resultSet.getLong("id"), resultSet.getString("signup_device_hash")),
                inviteCode);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private long countToday(long inviterId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM invite_reward WHERE inviter_id = ? AND " + SHANGHAI_TODAY, Long.class, inviterId);
        return count == null ? 0 : count;
    }

    private long countTotal(long inviterId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM invite_reward WHERE inviter_id = ?", Long.class, inviterId);
        return count == null ? 0 : count;
    }

    /**
     * 生成个人邀请码；并发注册时以唯一索引兜底重试。
     */
    private String ensureInviteCode(long userId) {
        String existing = jdbcTemplate.query(
                "SELECT invite_code FROM app_user WHERE id = ?",
                resultSet -> resultSet.next() ? resultSet.getString(1) : null,
                userId);
        if (existing != null && !existing.isBlank()) {
            return existing;
        }
        for (int attempt = 0; attempt < MAX_GENERATE_ATTEMPTS; attempt++) {
            String candidate = generateCode();
            try {
                int updated = jdbcTemplate.update(
                        "UPDATE app_user SET invite_code = ? WHERE id = ? AND invite_code IS NULL", candidate, userId);
                if (updated > 0) {
                    return candidate;
                }
                String current = jdbcTemplate.query(
                        "SELECT invite_code FROM app_user WHERE id = ?",
                        resultSet -> resultSet.next() ? resultSet.getString(1) : null,
                        userId);
                if (current != null && !current.isBlank()) {
                    return current;
                }
            } catch (DuplicateKeyException exception) {
                log.debug("邀请码碰撞，重试：attempt={}", attempt + 1);
            }
        }
        throw new BizException(ErrorCode.INTERNAL_ERROR, Map.of("reason", "INVITE_CODE_GENERATION_FAILED"));
    }

    private String generateCode() {
        StringBuilder builder = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            builder.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return builder.toString();
    }

    private static BizException fieldError(String field, String message) {
        return new BizException(ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of(field, message)));
    }

    private record Inviter(long id, String deviceHash) {}

    private record InviteStats(long invited, long interviewReward, long resumeReward) {}

    public record InviteProfile(
            String inviteCode,
            long invitedCount,
            long rewardedInterviewCredits,
            long rewardedResumeCredits,
            int rewardInviterInterview,
            int rewardInviteeResume,
            int dailyCap,
            int totalCap,
            long remainingToday,
            long remainingTotal) {}
}
