package com.offercoach.interview.api;

import com.offercoach.common.error.BizException;
import com.offercoach.common.error.ErrorCode;
import com.offercoach.identity.security.CurrentUser;
import com.offercoach.interview.api.dto.PageResponse;
import com.offercoach.interview.api.dto.jd.JdCreateRequest;
import com.offercoach.interview.api.dto.jd.JdCreatedResponse;
import com.offercoach.interview.api.dto.jd.JdDetailResponse;
import com.offercoach.interview.api.dto.jd.JdMatchRequest;
import com.offercoach.interview.api.dto.jd.JdMatchResponse;
import com.offercoach.interview.api.dto.jd.JdSummaryResponse;
import com.offercoach.interview.jd.application.JdService;
import com.offercoach.interview.jd.domain.MatchResult;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * JD 端点：创建、列表详情、软删与匹配分析。
 */
@RestController
@RequestMapping("/api/v1/jds")
public class JdController {

    private final JdService jdService;
    private final CurrentUser currentUser;

    public JdController(JdService jdService, CurrentUser currentUser) {
        this.jdService = jdService;
        this.currentUser = currentUser;
    }

    @PostMapping
    public ResponseEntity<JdCreatedResponse> create(@Valid @RequestBody JdCreateRequest request) {
        JdService.JdView view =
                jdService.create(currentUser.userId(), request.title(), request.company(), request.rawText());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new JdCreatedResponse(
                        Long.toString(view.id()), view.title(), view.structured(), timestamp(view.createdAt())));
    }

    @GetMapping
    public PageResponse<JdSummaryResponse> list(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        JdService.JdPage result = jdService.list(currentUser.userId(), page, size);
        List<JdSummaryResponse> items =
                result.items().stream().map(JdController::toSummary).toList();
        return PageResponse.of(items, result.total(), result.page(), result.size());
    }

    @GetMapping("/{id}")
    public JdDetailResponse detail(@PathVariable("id") String id) {
        return toDetail(jdService.detail(currentUser.userId(), parseId(id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") String id) {
        jdService.delete(currentUser.userId(), parseId(id));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/match")
    public JdMatchResponse match(@PathVariable("id") String id, @Valid @RequestBody JdMatchRequest request) {
        MatchResult result = jdService.match(currentUser.userId(), parseId(id), parseId(request.resumeId()));
        return JdMatchResponse.from(result);
    }

    private static JdSummaryResponse toSummary(JdService.JdSummaryView view) {
        return new JdSummaryResponse(
                Long.toString(view.id()),
                view.title(),
                view.company(),
                view.matchScore(),
                view.structured(),
                timestamp(view.createdAt()));
    }

    private static JdDetailResponse toDetail(JdService.JdView view) {
        return new JdDetailResponse(
                Long.toString(view.id()),
                view.title(),
                view.company(),
                view.rawText(),
                view.structured(),
                view.matchScore(),
                view.matchReport(),
                timestamp(view.createdAt()));
    }

    private static String timestamp(java.time.OffsetDateTime value) {
        return value == null ? null : value.toInstant().toString();
    }

    private static long parseId(String id) {
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException exception) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }
}
