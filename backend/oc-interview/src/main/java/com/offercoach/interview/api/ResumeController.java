package com.offercoach.interview.api;

import com.offercoach.common.error.ApiError;
import com.offercoach.common.error.BizException;
import com.offercoach.common.error.ErrorCode;
import com.offercoach.common.trace.TraceContext;
import com.offercoach.identity.security.CurrentUser;
import com.offercoach.interview.api.dto.PageResponse;
import com.offercoach.interview.api.dto.resume.ResumeDetailResponse;
import com.offercoach.interview.api.dto.resume.ResumeDownloadUrlResponse;
import com.offercoach.interview.api.dto.resume.ResumeManualRequest;
import com.offercoach.interview.api.dto.resume.ResumeReparseResponse;
import com.offercoach.interview.api.dto.resume.ResumeSummaryResponse;
import com.offercoach.interview.api.dto.resume.ResumeUpdateRequest;
import com.offercoach.interview.api.dto.resume.ResumeUploadResponse;
import com.offercoach.interview.resume.application.ResumeService;
import jakarta.validation.Valid;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

/**
 * 简历端点：上传、手录、列表详情、修正、软删、重解析与文件下载。
 */
@RestController
@RequestMapping("/api/v1/resumes")
public class ResumeController {

    private final ResumeService resumeService;
    private final CurrentUser currentUser;

    public ResumeController(ResumeService resumeService, CurrentUser currentUser) {
        this.resumeService = resumeService;
        this.currentUser = currentUser;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResumeUploadResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "isDefault", required = false) Boolean isDefault) {
        ResumeService.ResumeCreated created =
                resumeService.createFromFile(currentUser.userId(), file, title, Boolean.TRUE.equals(isDefault));
        return ResponseEntity.status(HttpStatus.CREATED).body(toUploadResponse(created));
    }

    @PostMapping("/manual")
    public ResponseEntity<ResumeUploadResponse> manual(@Valid @RequestBody ResumeManualRequest request) {
        boolean structured =
                request.structured() != null && !request.structured().isEmpty();
        if (structured) {
            ResumeService.ResumeCreated created = resumeService.createManualStructured(
                    currentUser.userId(),
                    request.title(),
                    request.structured(),
                    request.summary(),
                    Boolean.TRUE.equals(request.isDefault()));
            return ResponseEntity.status(HttpStatus.CREATED).body(toUploadResponse(created));
        }
        if (request.rawText() == null || request.rawText().isBlank()) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of("rawText", "简历内容不能为空")));
        }
        ResumeService.ResumeCreated created = resumeService.createManual(
                currentUser.userId(), request.title(), request.rawText(), Boolean.TRUE.equals(request.isDefault()));
        return ResponseEntity.status(HttpStatus.CREATED).body(toUploadResponse(created));
    }

    @GetMapping
    public PageResponse<ResumeSummaryResponse> list(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        ResumeService.ResumePage result = resumeService.list(currentUser.userId(), page, size);
        List<ResumeSummaryResponse> items =
                result.items().stream().map(ResumeController::toSummary).toList();
        return PageResponse.of(items, result.total(), result.page(), result.size());
    }

    @GetMapping("/{id}")
    public ResumeDetailResponse detail(@PathVariable("id") String id) {
        return toDetail(resumeService.detail(currentUser.userId(), parseId(id)));
    }

    @PatchMapping("/{id}")
    public ResumeDetailResponse update(@PathVariable("id") String id, @Valid @RequestBody ResumeUpdateRequest request) {
        ResumeService.ResumeView view = resumeService.update(
                currentUser.userId(), parseId(id), request.title(), request.structured(), request.isDefault());
        return toDetail(view);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") String id) {
        resumeService.delete(currentUser.userId(), parseId(id));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reparse")
    public ResponseEntity<ResumeReparseResponse> reparse(@PathVariable("id") String id) {
        ResumeService.ResumeReparse result = resumeService.reparse(currentUser.userId(), parseId(id));
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new ResumeReparseResponse(
                        Long.toString(result.id()), result.parseStatus(), result.estimatedReadyInMs()));
    }

    @GetMapping("/{id}/download-url")
    public ResumeDownloadUrlResponse downloadUrl(@PathVariable("id") String id) {
        ResumeService.DownloadUrl result = resumeService.downloadUrl(currentUser.userId(), parseId(id));
        return new ResumeDownloadUrlResponse(result.url(), result.expiresIn());
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> file(@PathVariable("id") String id) {
        ResumeService.ResumeFile file = resumeService.file(currentUser.userId(), parseId(id));
        String encodedName =
                URLEncoder.encode(file.fileName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedName)
                .contentType(mediaTypeOf(file.mimeType()))
                .contentLength(file.content().length)
                .body(new ByteArrayResource(file.content()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleUploadTooLarge(MaxUploadSizeExceededException exception) {
        return ResponseEntity.status(ErrorCode.FILE_TOO_LARGE.httpStatus())
                .body(ApiError.of(
                        ErrorCode.FILE_TOO_LARGE, null, Map.of(), TraceContext.requestId(), TraceContext.traceId()));
    }

    private static ResumeUploadResponse toUploadResponse(ResumeService.ResumeCreated created) {
        return new ResumeUploadResponse(
                Long.toString(created.id()),
                created.title(),
                created.parseStatus(),
                created.estimatedReadyInMs(),
                new ResumeUploadResponse.ResumeQuota(created.charged(), created.resumeCredits()));
    }

    private static ResumeSummaryResponse toSummary(ResumeService.ResumeSummaryView view) {
        return new ResumeSummaryResponse(
                Long.toString(view.id()),
                view.title(),
                view.parseStatus(),
                view.sourceType(),
                view.fileName(),
                view.pageCount(),
                view.summary(),
                view.isDefault(),
                view.parseError(),
                timestamp(view.createdAt()));
    }

    private static ResumeDetailResponse toDetail(ResumeService.ResumeView view) {
        return new ResumeDetailResponse(
                Long.toString(view.id()),
                view.title(),
                view.parseStatus(),
                view.sourceType(),
                view.fileName(),
                view.mimeType(),
                view.fileSize(),
                view.pageCount(),
                view.structured(),
                view.summary(),
                view.lowConfidenceFields(),
                view.parseError(),
                view.isDefault(),
                timestamp(view.createdAt()));
    }

    private static String timestamp(java.time.OffsetDateTime value) {
        return value == null ? null : value.toInstant().toString();
    }

    private static MediaType mediaTypeOf(String mimeType) {
        try {
            return MediaType.parseMediaType(mimeType);
        } catch (RuntimeException exception) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private static long parseId(String id) {
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException exception) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }
}
