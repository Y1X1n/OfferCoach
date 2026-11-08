package com.offercoach.admin.api;

import com.offercoach.admin.api.dto.AdminUserDtos.QuotaRequest;
import com.offercoach.admin.api.dto.AdminUserDtos.QuotaResponse;
import com.offercoach.admin.api.dto.AdminUserDtos.ResetPasswordRequest;
import com.offercoach.admin.api.dto.AdminUserDtos.RoleRequest;
import com.offercoach.admin.api.dto.AdminUserDtos.StatusRequest;
import com.offercoach.admin.api.dto.AdminUserDtos.UserResponse;
import com.offercoach.admin.api.dto.PageResponse;
import com.offercoach.admin.application.AdminUserService;
import com.offercoach.admin.application.model.AdminModels.Paged;
import com.offercoach.admin.application.model.AdminModels.QuotaAdjustment;
import com.offercoach.admin.application.model.AdminModels.User;
import com.offercoach.identity.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端用户与额度端点。
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService userService;
    private final CurrentUser currentUser;

    public AdminUserController(AdminUserService userService, CurrentUser currentUser) {
        this.userService = userService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public PageResponse<UserResponse> list(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status) {
        Paged<User> result = userService.list(page, size, keyword, status);
        return PageResponse.of(
                result.items().stream().map(AdminUserController::toResponse).toList(),
                result.total(),
                result.page(),
                result.size());
    }

    @PatchMapping("/{id}/status")
    public UserResponse updateStatus(@PathVariable("id") String id, @Valid @RequestBody StatusRequest request) {
        User updated =
                userService.updateStatus(currentUser.userId(), AdminWebSupport.parseId(id, "id"), request.status());
        return toResponse(updated);
    }

    @PatchMapping("/{id}/role")
    public UserResponse updateRole(@PathVariable("id") String id, @Valid @RequestBody RoleRequest request) {
        User updated = userService.updateRole(currentUser.userId(), AdminWebSupport.parseId(id, "id"), request.role());
        return toResponse(updated);
    }

    @PostMapping("/{id}/unlock")
    public UserResponse unlock(@PathVariable("id") String id) {
        return toResponse(userService.unlock(currentUser.userId(), AdminWebSupport.parseId(id, "id")));
    }

    @PostMapping("/{id}/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@PathVariable("id") String id, @Valid @RequestBody ResetPasswordRequest request) {
        userService.resetPassword(currentUser.userId(), AdminWebSupport.parseId(id, "id"), request.password());
    }

    @PostMapping("/{id}/quota")
    public QuotaResponse adjustQuota(@PathVariable("id") String id, @Valid @RequestBody QuotaRequest request) {
        long userId = AdminWebSupport.parseId(id, "id");
        QuotaAdjustment adjustment = userService.adjustQuota(
                currentUser.userId(),
                userId,
                request.type(),
                request.delta(),
                request.reason(),
                request.idempotencyKey());
        return new QuotaResponse(
                Long.toString(userId),
                adjustment.type(),
                adjustment.delta(),
                adjustment.balanceAfter(),
                adjustment.replayed());
    }

    private static UserResponse toResponse(User user) {
        return new UserResponse(
                Long.toString(user.id()),
                user.email(),
                user.nickname(),
                user.role(),
                user.status(),
                user.planCode(),
                user.interviewCredits(),
                user.resumeCredits(),
                AdminWebSupport.timestamp(user.createdAt()),
                AdminWebSupport.timestamp(user.lastLoginAt()));
    }
}
