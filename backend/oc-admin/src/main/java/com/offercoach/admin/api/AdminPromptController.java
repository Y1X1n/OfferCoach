package com.offercoach.admin.api;

import com.offercoach.admin.api.dto.AdminPromptDtos.ActiveVersionResponse;
import com.offercoach.admin.api.dto.AdminPromptDtos.CreateTemplateRequest;
import com.offercoach.admin.api.dto.AdminPromptDtos.CreateVersionRequest;
import com.offercoach.admin.api.dto.AdminPromptDtos.TemplateResponse;
import com.offercoach.admin.api.dto.AdminPromptDtos.VersionResponse;
import com.offercoach.admin.application.AdminPromptService;
import com.offercoach.admin.application.model.AdminModels.PromptTemplate;
import com.offercoach.admin.application.model.AdminModels.PromptVersion;
import com.offercoach.identity.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Prompt 模板与版本管理端点。
 */
@RestController
@RequestMapping("/api/v1/admin/prompts")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPromptController {

    private final AdminPromptService promptService;
    private final CurrentUser currentUser;

    public AdminPromptController(AdminPromptService promptService, CurrentUser currentUser) {
        this.promptService = promptService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<TemplateResponse> list() {
        return promptService.listTemplates().stream()
                .map(AdminPromptController::toTemplateResponse)
                .toList();
    }

    @PostMapping
    public TemplateResponse createTemplate(@Valid @RequestBody CreateTemplateRequest request) {
        PromptTemplate created = promptService.createTemplate(
                currentUser.userId(), request.code(), request.name(), request.scene(), request.description());
        return toTemplateResponse(created);
    }

    @GetMapping("/{code}/versions")
    public List<VersionResponse> versions(@PathVariable("code") String code) {
        return promptService.listVersions(code).stream()
                .map(AdminPromptController::toVersionResponse)
                .toList();
    }

    @PostMapping("/{code}/versions")
    public VersionResponse createVersion(
            @PathVariable("code") String code, @Valid @RequestBody CreateVersionRequest request) {
        PromptVersion created = promptService.createVersion(
                currentUser.userId(),
                code,
                request.version(),
                request.content(),
                request.variablesSchema(),
                request.modelConfig());
        return toVersionResponse(created);
    }

    @PostMapping("/{code}/versions/{version}/publish")
    public VersionResponse publish(@PathVariable("code") String code, @PathVariable("version") String version) {
        return toVersionResponse(promptService.publish(currentUser.userId(), code, version));
    }

    @GetMapping("/{code}/active")
    public ActiveVersionResponse active(@PathVariable("code") String code) {
        PromptVersion version = promptService.active(code);
        return new ActiveVersionResponse(
                version.templateCode(),
                version.version(),
                version.content(),
                version.variablesSchema(),
                version.modelConfig(),
                version.contentHash(),
                AdminWebSupport.timestamp(version.publishedAt()));
    }

    private static TemplateResponse toTemplateResponse(PromptTemplate template) {
        return new TemplateResponse(
                Long.toString(template.id()),
                template.code(),
                template.name(),
                template.scene(),
                template.description(),
                template.activeVersion(),
                AdminWebSupport.timestamp(template.createdAt()),
                AdminWebSupport.timestamp(template.updatedAt()));
    }

    private static VersionResponse toVersionResponse(PromptVersion version) {
        return new VersionResponse(
                Long.toString(version.id()),
                version.templateCode(),
                version.version(),
                version.status(),
                version.content(),
                version.variablesSchema(),
                version.modelConfig(),
                version.contentHash(),
                AdminWebSupport.id(version.createdBy()),
                AdminWebSupport.timestamp(version.publishedAt()),
                AdminWebSupport.timestamp(version.createdAt()));
    }
}
