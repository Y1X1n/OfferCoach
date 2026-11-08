package com.offercoach.identity.api;

import com.offercoach.identity.api.dto.QuotaResponse;
import com.offercoach.identity.application.quota.QuotaService;
import com.offercoach.identity.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前用户额度查询；需要认证，userId 由 JWT 的 sub 提供。
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class QuotaController {

    private final QuotaService quotaService;
    private final CurrentUser currentUser;

    @GetMapping("/quota")
    public QuotaResponse currentQuota() {
        return QuotaResponse.from(quotaService.snapshot(currentUser.userId()));
    }
}
