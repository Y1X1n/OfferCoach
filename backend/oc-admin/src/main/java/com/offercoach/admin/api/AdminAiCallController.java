package com.offercoach.admin.api;

import com.offercoach.admin.api.dto.AdminAiCallDtos.AiCallResponse;
import com.offercoach.admin.api.dto.PageResponse;
import com.offercoach.admin.application.AdminAiCallService;
import com.offercoach.admin.application.model.AdminModels.AiCall;
import com.offercoach.admin.application.model.AdminModels.Paged;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 调用日志只读端点。
 */
@RestController
@RequestMapping("/api/v1/admin/ai-calls")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAiCallController {

    private final AdminAiCallService aiCallService;

    public AdminAiCallController(AdminAiCallService aiCallService) {
        this.aiCallService = aiCallService;
    }

    @GetMapping
    public PageResponse<AiCallResponse> list(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "scene", required = false) String scene,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "userId", required = false) String userId) {
        Paged<AiCall> result = aiCallService.list(page, size, scene, status, userId);
        return PageResponse.of(
                result.items().stream().map(AdminAiCallController::toResponse).toList(),
                result.total(),
                result.page(),
                result.size());
    }

    private static AiCallResponse toResponse(AiCall call) {
        return new AiCallResponse(
                Long.toString(call.id()),
                AdminWebSupport.id(call.userId()),
                call.scene(),
                call.provider(),
                call.model(),
                call.status(),
                call.tokensIn(),
                call.tokensOut(),
                call.firstTokenMs(),
                call.latencyMs(),
                call.errorCode(),
                AdminWebSupport.timestamp(call.createdAt()));
    }
}
