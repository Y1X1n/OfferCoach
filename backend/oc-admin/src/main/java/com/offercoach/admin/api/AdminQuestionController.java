package com.offercoach.admin.api;

import com.offercoach.admin.api.dto.AdminQuestionDtos.CreateQuestionRequest;
import com.offercoach.admin.api.dto.AdminQuestionDtos.ImportBatchResponse;
import com.offercoach.admin.api.dto.AdminQuestionDtos.ImportItem;
import com.offercoach.admin.api.dto.AdminQuestionDtos.ImportRequest;
import com.offercoach.admin.api.dto.AdminQuestionDtos.QuestionResponse;
import com.offercoach.admin.api.dto.AdminQuestionDtos.UpdateQuestionRequest;
import com.offercoach.admin.api.dto.PageResponse;
import com.offercoach.admin.application.AdminQuestionService;
import com.offercoach.admin.application.model.AdminModels;
import com.offercoach.admin.application.model.AdminModels.ImportBatch;
import com.offercoach.admin.application.model.AdminModels.Paged;
import com.offercoach.admin.application.model.AdminModels.Question;
import com.offercoach.admin.application.model.AdminModels.QuestionWrite;
import com.offercoach.identity.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端题库端点：筛选、草稿维护、发布归档、JSON 导入与批次查询。
 */
@RestController
@RequestMapping("/api/v1/admin/questions")
@PreAuthorize("hasRole('ADMIN')")
public class AdminQuestionController {

    private final AdminQuestionService questionService;
    private final CurrentUser currentUser;

    public AdminQuestionController(AdminQuestionService questionService, CurrentUser currentUser) {
        this.questionService = questionService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public PageResponse<QuestionResponse> list(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "jobFamily", required = false) String jobFamily,
            @RequestParam(value = "dimension", required = false) String dimension,
            @RequestParam(value = "difficulty", required = false) String difficulty,
            @RequestParam(value = "keyword", required = false) String keyword) {
        Paged<Question> result = questionService.list(page, size, status, jobFamily, dimension, difficulty, keyword);
        return PageResponse.of(
                result.items().stream().map(AdminQuestionController::toResponse).toList(),
                result.total(),
                result.page(),
                result.size());
    }

    @PostMapping
    public QuestionResponse create(@Valid @RequestBody CreateQuestionRequest request) {
        Question created = questionService.create(
                currentUser.userId(),
                new QuestionWrite(
                        request.title(),
                        request.jobFamily(),
                        request.dimension(),
                        request.difficulty(),
                        request.tags(),
                        request.stem(),
                        request.referenceAnswer(),
                        request.scoringPoints(),
                        request.company(),
                        request.sourceUrl(),
                        request.sourceLicense(),
                        request.language()));
        return toResponse(created);
    }

    @PatchMapping("/{id}")
    public QuestionResponse update(@PathVariable("id") String id, @Valid @RequestBody UpdateQuestionRequest request) {
        Question updated = questionService.update(
                currentUser.userId(),
                AdminWebSupport.parseId(id, "id"),
                new QuestionWrite(
                        request.title(),
                        request.jobFamily(),
                        request.dimension(),
                        request.difficulty(),
                        request.tags(),
                        request.stem(),
                        request.referenceAnswer(),
                        request.scoringPoints(),
                        request.company(),
                        request.sourceUrl(),
                        request.sourceLicense(),
                        request.language()));
        return toResponse(updated);
    }

    @PostMapping("/{id}/publish")
    public QuestionResponse publish(@PathVariable("id") String id) {
        return toResponse(questionService.publish(currentUser.userId(), AdminWebSupport.parseId(id, "id")));
    }

    @PostMapping("/{id}/archive")
    public QuestionResponse archive(@PathVariable("id") String id) {
        return toResponse(questionService.archive(currentUser.userId(), AdminWebSupport.parseId(id, "id")));
    }

    @PostMapping("/import")
    public ImportBatchResponse importQuestions(@Valid @RequestBody ImportRequest request) {
        List<AdminModels.ImportItem> items = request.items().stream()
                .map(AdminQuestionController::toImportItem)
                .toList();
        ImportBatch batch = questionService.importQuestions(currentUser.userId(), request.sourceRepo(), items);
        return toBatchResponse(batch);
    }

    @GetMapping("/batches")
    public PageResponse<ImportBatchResponse> batches(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        Paged<ImportBatch> result = questionService.listBatches(page, size);
        return PageResponse.of(
                result.items().stream()
                        .map(AdminQuestionController::toBatchResponse)
                        .toList(),
                result.total(),
                result.page(),
                result.size());
    }

    private static AdminModels.ImportItem toImportItem(ImportItem item) {
        return new AdminModels.ImportItem(
                item.title(),
                item.jobFamily(),
                item.dimension(),
                item.difficulty(),
                item.tags(),
                item.stem(),
                item.referenceAnswer(),
                item.scoringPoints(),
                item.company(),
                item.sourceUrl(),
                item.sourceLicense(),
                item.language());
    }

    private static QuestionResponse toResponse(Question question) {
        return new QuestionResponse(
                Long.toString(question.id()),
                question.title(),
                question.stem(),
                question.jobFamily(),
                question.dimension(),
                question.difficulty(),
                question.status(),
                question.source(),
                question.sourceRepo(),
                question.company(),
                question.tags(),
                AdminWebSupport.timestamp(question.createdAt()));
    }

    private static ImportBatchResponse toBatchResponse(ImportBatch batch) {
        return new ImportBatchResponse(
                Long.toString(batch.id()),
                batch.sourceRepo(),
                batch.totalCount(),
                batch.successCount(),
                batch.failedCount(),
                batch.skippedCount(),
                batch.status(),
                batch.errorReport(),
                AdminWebSupport.id(batch.createdBy()),
                AdminWebSupport.timestamp(batch.createdAt()),
                AdminWebSupport.timestamp(batch.finishedAt()));
    }
}
