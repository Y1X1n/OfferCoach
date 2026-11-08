package com.offercoach.admin.api;

import com.offercoach.admin.api.dto.AdminAiConfigDtos.AiConfigResponse;
import com.offercoach.admin.api.dto.AdminAiConfigDtos.AiConfigTestResponse;
import com.offercoach.admin.api.dto.AdminAiConfigDtos.UpdateAiConfigRequest;
import com.offercoach.admin.application.AdminAiConfigService;
import com.offercoach.ai.AiProvider;
import com.offercoach.ai.AiRuntimeConfigService.AiRuntimeSnapshot;
import com.offercoach.identity.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端 AI 运行时配置端点：在线切换 URL/Key/模型，保存后立即生效。
 */
@RestController
@RequestMapping("/api/v1/admin/ai-config")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAiConfigController {

    private static final int TEST_MESSAGE_MAX_LENGTH = 200;

    private final AdminAiConfigService configService;
    private final CurrentUser currentUser;

    public AdminAiConfigController(AdminAiConfigService configService, CurrentUser currentUser) {
        this.configService = configService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public AiConfigResponse get() {
        return toResponse(configService.snapshot());
    }

    @PutMapping
    public AiConfigResponse update(@Valid @RequestBody UpdateAiConfigRequest request) {
        AiRuntimeSnapshot snapshot = configService.update(
                currentUser.userId(),
                Boolean.TRUE.equals(request.enabled()),
                request.baseUrl(),
                request.model(),
                request.smallModel(),
                request.apiKey(),
                Boolean.TRUE.equals(request.clearApiKey()));
        return toResponse(snapshot);
    }

    @PostMapping("/test")
    public AiConfigTestResponse test() {
        AiProvider.AiResult result = configService.test();
        return new AiConfigTestResponse(true, result.model(), result.latencyMs(), testMessage(result.text()));
    }

    private static String testMessage(String text) {
        if (text == null || text.isBlank()) {
            return "连接成功";
        }
        String normalized = text.strip();
        return normalized.length() <= TEST_MESSAGE_MAX_LENGTH
                ? normalized
                : normalized.substring(0, TEST_MESSAGE_MAX_LENGTH);
    }

    private static AiConfigResponse toResponse(AiRuntimeSnapshot snapshot) {
        return new AiConfigResponse(
                snapshot.enabled(),
                snapshot.baseUrl(),
                snapshot.model(),
                snapshot.smallModel(),
                snapshot.hasApiKey(),
                snapshot.apiKeyMasked(),
                AdminWebSupport.timestamp(snapshot.updatedAt()),
                AdminWebSupport.id(snapshot.updatedBy()));
    }
}
