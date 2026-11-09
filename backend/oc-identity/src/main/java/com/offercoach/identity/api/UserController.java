package com.offercoach.identity.api;

import com.offercoach.identity.api.dto.InviteProfileResponse;
import com.offercoach.identity.api.dto.MeResponse;
import com.offercoach.identity.api.dto.UpdateMeRequest;
import com.offercoach.identity.application.UserService;
import com.offercoach.identity.application.invite.InviteService;
import com.offercoach.identity.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前用户资料端点。
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;
    private final InviteService inviteService;
    private final CurrentUser currentUser;

    public UserController(UserService userService, InviteService inviteService, CurrentUser currentUser) {
        this.userService = userService;
        this.inviteService = inviteService;
        this.currentUser = currentUser;
    }

    @GetMapping("/me")
    public MeResponse me() {
        return toResponse(userService.me(currentUser.userId()));
    }

    /**
     * 个人邀请名片：邀请码、已邀请人数、累计奖励与剩余名额。
     */
    @GetMapping("/me/invite")
    public InviteProfileResponse invite() {
        InviteService.InviteProfile profile = inviteService.profile(currentUser.userId());
        return new InviteProfileResponse(
                profile.inviteCode(),
                profile.invitedCount(),
                profile.rewardedInterviewCredits(),
                profile.rewardedResumeCredits(),
                profile.rewardInviterInterview(),
                profile.rewardInviteeResume(),
                profile.dailyCap(),
                profile.totalCap(),
                profile.remainingToday(),
                profile.remainingTotal());
    }

    @PatchMapping("/me")
    public MeResponse update(@Valid @RequestBody UpdateMeRequest request) {
        return toResponse(userService.update(
                currentUser.userId(),
                request.nickname(),
                request.timezone(),
                request.avatarUrl(),
                request.allowRawResumeToLlm()));
    }

    private MeResponse toResponse(UserService.UserProfile profile) {
        return new MeResponse(
                Long.toString(profile.id()),
                profile.email(),
                profile.nickname(),
                profile.avatarUrl(),
                profile.role(),
                profile.timezone(),
                profile.planCode(),
                profile.createdAt(),
                profile.allowRawResumeToLlm());
    }
}
