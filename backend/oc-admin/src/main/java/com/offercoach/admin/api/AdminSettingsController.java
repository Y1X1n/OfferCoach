package com.offercoach.admin.api;

import com.offercoach.admin.api.dto.AdminSettingsDtos.PlatformSettingsResponse;
import com.offercoach.admin.api.dto.AdminSettingsDtos.UpdatePlatformSettingsRequest;
import com.offercoach.admin.audit.AuditLogger;
import com.offercoach.identity.application.settings.PlatformSettingsService;
import com.offercoach.identity.application.settings.PlatformSettingsService.Command;
import com.offercoach.identity.application.settings.PlatformSettingsService.Snapshot;
import com.offercoach.identity.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端平台策略端点：在线调节每日模拟面试/解析次数、注册开关、邀请奖励与注册风控阈值。
 */
@RestController
@RequestMapping("/api/v1/admin/settings/quota")
@PreAuthorize("hasRole('ADMIN')")
public class AdminSettingsController {

    private static final String ACTION_UPDATE = "SETTINGS_QUOTA_UPDATE";
    private static final String TARGET_TYPE = "PLATFORM_SETTINGS";
    private static final String TARGET_ID = "1";

    private final PlatformSettingsService settingsService;
    private final AuditLogger auditLogger;
    private final CurrentUser currentUser;

    public AdminSettingsController(
            PlatformSettingsService settingsService, AuditLogger auditLogger, CurrentUser currentUser) {
        this.settingsService = settingsService;
        this.auditLogger = auditLogger;
        this.currentUser = currentUser;
    }

    @GetMapping
    public PlatformSettingsResponse get() {
        return toResponse(settingsService.snapshot());
    }

    @PutMapping
    public PlatformSettingsResponse update(@Valid @RequestBody UpdatePlatformSettingsRequest request) {
        Map<String, Object> before = auditView(settingsService.snapshot());
        Snapshot updated = settingsService.update(
                currentUser.userId(),
                new Command(
                        Boolean.TRUE.equals(request.registrationOpen()),
                        request.dailyInterviewLimit(),
                        request.dailyResumeLimit(),
                        request.freeMonthlyInterviews(),
                        request.freeMonthlyResumes(),
                        request.inviteRewardInviterInterview(),
                        request.inviteRewardInviteeResume(),
                        request.inviteDailyCap(),
                        request.inviteTotalCap(),
                        request.registrationIpDailyCap(),
                        request.registrationDeviceDailyCap()));
        auditLogger.log(currentUser.userId(), ACTION_UPDATE, TARGET_TYPE, TARGET_ID, before, auditView(updated));
        return toResponse(updated);
    }

    private static Map<String, Object> auditView(Snapshot snapshot) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("registrationOpen", snapshot.registrationOpen());
        view.put("dailyInterviewLimit", snapshot.dailyInterviewLimit());
        view.put("dailyResumeLimit", snapshot.dailyResumeLimit());
        view.put("freeMonthlyInterviews", snapshot.freeMonthlyInterviews());
        view.put("freeMonthlyResumes", snapshot.freeMonthlyResumes());
        view.put("inviteRewardInviterInterview", snapshot.inviteRewardInviterInterview());
        view.put("inviteRewardInviteeResume", snapshot.inviteRewardInviteeResume());
        view.put("inviteDailyCap", snapshot.inviteDailyCap());
        view.put("inviteTotalCap", snapshot.inviteTotalCap());
        view.put("registrationIpDailyCap", snapshot.registrationIpDailyCap());
        view.put("registrationDeviceDailyCap", snapshot.registrationDeviceDailyCap());
        return view;
    }

    private static PlatformSettingsResponse toResponse(Snapshot snapshot) {
        return new PlatformSettingsResponse(
                snapshot.registrationOpen(),
                snapshot.dailyInterviewLimit(),
                snapshot.dailyResumeLimit(),
                snapshot.freeMonthlyInterviews(),
                snapshot.freeMonthlyResumes(),
                snapshot.inviteRewardInviterInterview(),
                snapshot.inviteRewardInviteeResume(),
                snapshot.inviteDailyCap(),
                snapshot.inviteTotalCap(),
                snapshot.registrationIpDailyCap(),
                snapshot.registrationDeviceDailyCap(),
                AdminWebSupport.timestamp(snapshot.updatedAt()),
                AdminWebSupport.id(snapshot.updatedBy()));
    }
}
