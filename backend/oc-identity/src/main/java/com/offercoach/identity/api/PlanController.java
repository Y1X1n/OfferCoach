package com.offercoach.identity.api;

import com.offercoach.identity.api.dto.PlanResponse;
import com.offercoach.identity.application.quota.QuotaService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 套餐列表；公开接口，仅返回 active 套餐。
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PlanController {

    private final QuotaService quotaService;

    @GetMapping("/plans")
    public List<PlanResponse> plans() {
        return quotaService.listPlans().stream().map(PlanResponse::from).toList();
    }
}
