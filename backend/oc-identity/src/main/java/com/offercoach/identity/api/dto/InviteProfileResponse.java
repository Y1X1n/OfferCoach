package com.offercoach.identity.api.dto;

/**
 * 个人邀请名片：邀请码、已邀请人数、累计奖励与剩余名额。
 */
public record InviteProfileResponse(
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
