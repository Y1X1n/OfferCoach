package com.offercoach.identity.api.dto;

/**
 * 注册页运行时策略：是否开放、需要哪类验证码、邀请奖励与每日额度说明。
 */
public record RegistrationPolicyResponse(
        boolean registrationOpen,
        boolean requireCaptcha,
        boolean requireVerifyCode,
        boolean inviteOptional,
        int inviteRewardInviterInterview,
        int inviteRewardInviteeResume,
        int dailyInterviewLimit,
        int dailyResumeLimit) {}
