package com.offercoach.admin.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 管理端平台运行策略：注册开关、每日额度、邀请奖励与注册风控阈值。
 */
public final class AdminSettingsDtos {

    private AdminSettingsDtos() {}

    public record PlatformSettingsResponse(
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
            String updatedAt,
            String updatedBy) {}

    public record UpdatePlatformSettingsRequest(
            @NotNull Boolean registrationOpen,
            @NotNull @Min(0) @Max(10000) Integer dailyInterviewLimit,
            @NotNull @Min(0) @Max(10000) Integer dailyResumeLimit,
            @NotNull @Min(0) @Max(1000) Integer freeMonthlyInterviews,
            @NotNull @Min(0) @Max(1000) Integer freeMonthlyResumes,
            @NotNull @Min(0) @Max(100) Integer inviteRewardInviterInterview,
            @NotNull @Min(0) @Max(100) Integer inviteRewardInviteeResume,
            @NotNull @Min(0) @Max(1000) Integer inviteDailyCap,
            @NotNull @Min(0) @Max(10000) Integer inviteTotalCap,
            @NotNull @Min(0) @Max(10000) Integer registrationIpDailyCap,
            @NotNull @Min(0) @Max(1000) Integer registrationDeviceDailyCap) {}
}
