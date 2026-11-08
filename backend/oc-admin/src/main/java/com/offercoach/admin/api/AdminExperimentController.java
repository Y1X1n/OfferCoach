package com.offercoach.admin.api;

import com.offercoach.admin.api.dto.AdminExperimentDtos.CreateExperimentRequest;
import com.offercoach.admin.api.dto.AdminExperimentDtos.ExperimentResponse;
import com.offercoach.admin.api.dto.AdminExperimentDtos.StatusRequest;
import com.offercoach.admin.api.dto.PageResponse;
import com.offercoach.admin.application.AdminExperimentService;
import com.offercoach.admin.application.model.AdminModels.Experiment;
import com.offercoach.admin.application.model.AdminModels.Paged;
import com.offercoach.identity.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * A/B 实验最小管理端点。
 */
@RestController
@RequestMapping("/api/v1/admin/experiments")
@PreAuthorize("hasRole('ADMIN')")
public class AdminExperimentController {

    private final AdminExperimentService experimentService;
    private final CurrentUser currentUser;

    public AdminExperimentController(AdminExperimentService experimentService, CurrentUser currentUser) {
        this.experimentService = experimentService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public PageResponse<ExperimentResponse> list(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        Paged<Experiment> result = experimentService.list(page, size);
        return PageResponse.of(
                result.items().stream()
                        .map(AdminExperimentController::toResponse)
                        .toList(),
                result.total(),
                result.page(),
                result.size());
    }

    @PostMapping
    public ExperimentResponse create(@Valid @RequestBody CreateExperimentRequest request) {
        Experiment created = experimentService.create(
                currentUser.userId(),
                request.templateCode(),
                request.name(),
                request.trafficPercent(),
                request.variants(),
                request.guardrails());
        return toResponse(created);
    }

    @PostMapping("/{id}/status")
    public ExperimentResponse updateStatus(@PathVariable("id") String id, @Valid @RequestBody StatusRequest request) {
        Experiment updated = experimentService.updateStatus(
                currentUser.userId(), AdminWebSupport.parseId(id, "id"), request.status());
        return toResponse(updated);
    }

    private static ExperimentResponse toResponse(Experiment experiment) {
        return new ExperimentResponse(
                Long.toString(experiment.id()),
                experiment.templateCode(),
                experiment.name(),
                experiment.status(),
                experiment.trafficPercent(),
                experiment.variants(),
                experiment.guardrails(),
                AdminWebSupport.timestamp(experiment.startAt()),
                AdminWebSupport.timestamp(experiment.endAt()),
                AdminWebSupport.timestamp(experiment.createdAt()));
    }
}
