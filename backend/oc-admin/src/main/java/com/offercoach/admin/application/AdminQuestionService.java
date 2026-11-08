package com.offercoach.admin.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offercoach.admin.application.model.AdminModels.ImportBatch;
import com.offercoach.admin.application.model.AdminModels.ImportItem;
import com.offercoach.admin.application.model.AdminModels.ImportedQuestion;
import com.offercoach.admin.application.model.AdminModels.Paged;
import com.offercoach.admin.application.model.AdminModels.Question;
import com.offercoach.admin.application.model.AdminModels.QuestionWrite;
import com.offercoach.admin.application.port.AdminQuestionRepository;
import com.offercoach.admin.application.support.PageQuery;
import com.offercoach.admin.application.support.QuestionImportValidator;
import com.offercoach.admin.application.support.QuestionImportValidator.InvalidQuestionFieldException;
import com.offercoach.admin.application.support.QuestionImportValidator.ValidatedItem;
import com.offercoach.admin.audit.AuditLogger;
import com.offercoach.common.error.BizException;
import com.offercoach.common.error.ErrorCode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 题库管理：分页筛选、草稿维护、发布/归档与 JSON 批量导入。
 */
@Service
public class AdminQuestionService {

    private static final Logger log = LoggerFactory.getLogger(AdminQuestionService.class);
    private static final Set<String> STATUSES = Set.of("DRAFT", "PUBLISHED", "ARCHIVED");
    private static final String SOURCE_REPO_MAX = "长度不能超过 160";
    private static final int SOURCE_REPO_MAX_LENGTH = 160;
    private static final int MAX_ERRORS = 50;
    private static final String DEFAULT_LANGUAGE = "zh-CN";
    private static final String ACTION_CREATE = "QUESTION_CREATE";
    private static final String ACTION_UPDATE = "QUESTION_UPDATE";
    private static final String ACTION_PUBLISH = "QUESTION_PUBLISH";
    private static final String ACTION_ARCHIVE = "QUESTION_ARCHIVE";
    private static final String ACTION_IMPORT = "QUESTION_IMPORT";
    private static final String TARGET_QUESTION = "QUESTION";
    private static final String TARGET_BATCH = "QUESTION_IMPORT_BATCH";

    private final AdminQuestionRepository questionRepository;
    private final AuditLogger auditLogger;
    private final ObjectMapper objectMapper;

    public AdminQuestionService(
            AdminQuestionRepository questionRepository, AuditLogger auditLogger, ObjectMapper objectMapper) {
        this.questionRepository = questionRepository;
        this.auditLogger = auditLogger;
        this.objectMapper = objectMapper;
    }

    /**
     * 分页筛选；筛选枚举非法时返回 400 而不是静默忽略。
     */
    public Paged<Question> list(
            int page, int size, String status, String jobFamily, String dimension, String difficulty, String keyword) {
        PageQuery query = PageQuery.of(page, size);
        String statusFilter = guard(() -> normalizeStatusFilter(status));
        String familyFilter = blankToNull(jobFamily);
        String dimensionFilter = guard(() -> nullOrDimension(dimension));
        String difficultyFilter = guard(() -> nullOrDifficulty(difficulty));
        String keywordFilter = blankToNull(keyword);
        List<Question> items = questionRepository.search(
                statusFilter,
                familyFilter,
                dimensionFilter,
                difficultyFilter,
                keywordFilter,
                (int) query.offset(),
                query.size());
        long total =
                questionRepository.count(statusFilter, familyFilter, dimensionFilter, difficultyFilter, keywordFilter);
        return new Paged<>(items, total, query.page(), query.size());
    }

    @Transactional
    public Question create(long actorId, QuestionWrite raw) {
        QuestionWrite write = normalizeCreate(raw);
        long id = questionRepository.insert(write, actorId);
        Question created = requireQuestion(id);
        auditLogger.log(actorId, ACTION_CREATE, TARGET_QUESTION, Long.toString(id), null, summary(created));
        return created;
    }

    @Transactional
    public Question update(long actorId, long id, QuestionWrite raw) {
        Question before = requireQuestion(id);
        if ("ARCHIVED".equals(before.status())) {
            throw stateConflict(before);
        }
        QuestionWrite patch = normalizePatch(raw);
        questionRepository.update(id, patch);
        Question after = requireQuestion(id);
        auditLogger.log(actorId, ACTION_UPDATE, TARGET_QUESTION, Long.toString(id), summary(before), summary(after));
        return after;
    }

    @Transactional
    public Question publish(long actorId, long id) {
        Question before = requireQuestion(id);
        if ("ARCHIVED".equals(before.status())) {
            throw stateConflict(before);
        }
        if ("PUBLISHED".equals(before.status())) {
            return before;
        }
        questionRepository.updateStatus(id, "PUBLISHED");
        Question after = requireQuestion(id);
        auditLogger.log(actorId, ACTION_PUBLISH, TARGET_QUESTION, Long.toString(id), summary(before), summary(after));
        return after;
    }

    @Transactional
    public Question archive(long actorId, long id) {
        Question before = requireQuestion(id);
        if ("ARCHIVED".equals(before.status())) {
            return before;
        }
        questionRepository.updateStatus(id, "ARCHIVED");
        Question after = requireQuestion(id);
        auditLogger.log(actorId, ACTION_ARCHIVE, TARGET_QUESTION, Long.toString(id), summary(before), summary(after));
        return after;
    }

    /**
     * JSON 批量导入：先建 PENDING 批次，逐条校验与写入，最后落 DONE/FAILED 统计。
     * 不包整体事务，避免单条失败导致批次计数无法写回；逐条写入由唯一索引保证幂等。
     */
    public ImportBatch importQuestions(long actorId, String sourceRepo, List<ImportItem> items) {
        String repo = requireSourceRepo(sourceRepo);
        if (items == null || items.isEmpty()) {
            throw fieldError("items", "不能为空");
        }
        if (items.size() > QuestionImportValidator.MAX_IMPORT_ITEMS) {
            throw fieldError("items", "单次最多 " + QuestionImportValidator.MAX_IMPORT_ITEMS + " 条");
        }

        long batchId = questionRepository.insertImportBatch(repo, items.size(), actorId);
        int success = 0;
        int failed = 0;
        int skipped = 0;
        List<Map<String, Object>> errors = new ArrayList<>();
        try {
            for (int index = 0; index < items.size(); index++) {
                try {
                    ValidatedItem validated = QuestionImportValidator.validateImportItem(items.get(index));
                    ImportedQuestion question = new ImportedQuestion(
                            validated.title(),
                            validated.jobFamily(),
                            validated.dimension(),
                            validated.difficulty(),
                            validated.tags(),
                            validated.stem(),
                            validated.referenceAnswer(),
                            validated.scoringPoints(),
                            validated.company(),
                            validated.sourceUrl(),
                            validated.sourceLicense(),
                            validated.language());
                    String sourcePath = "admin-import:" + batchId + ":" + index;
                    int inserted = questionRepository.insertImported(question, repo, sourcePath, actorId);
                    if (inserted > 0) {
                        success++;
                    } else {
                        skipped++;
                    }
                } catch (InvalidQuestionFieldException exception) {
                    failed++;
                    addError(errors, index, exception.field(), exception.getMessage());
                } catch (RuntimeException exception) {
                    failed++;
                    addError(errors, index, null, exception.getClass().getSimpleName());
                }
            }
            questionRepository.finishImportBatch(batchId, "DONE", success, failed, skipped, toJson(errors));
        } catch (RuntimeException exception) {
            markBatchFailed(batchId, success, failed, skipped, errors);
            throw exception instanceof BizException bizException
                    ? bizException
                    : new BizException(ErrorCode.INTERNAL_ERROR);
        }

        ImportBatch batch = questionRepository
                .findBatch(batchId)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("batchId", batchId)));
        Map<String, Object> auditAfter = new LinkedHashMap<>();
        auditAfter.put("sourceRepo", repo);
        auditAfter.put("totalCount", items.size());
        auditAfter.put("successCount", success);
        auditAfter.put("failedCount", failed);
        auditAfter.put("skippedCount", skipped);
        auditAfter.put("status", batch.status());
        auditLogger.log(actorId, ACTION_IMPORT, TARGET_BATCH, Long.toString(batchId), null, auditAfter);
        return batch;
    }

    public Paged<ImportBatch> listBatches(int page, int size) {
        PageQuery query = PageQuery.of(page, size);
        List<ImportBatch> items = questionRepository.listBatches((int) query.offset(), query.size());
        long total = questionRepository.countBatches();
        return new Paged<>(items, total, query.page(), query.size());
    }

    private Question requireQuestion(long id) {
        return questionRepository
                .findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("id", id)));
    }

    private QuestionWrite normalizeCreate(QuestionWrite raw) {
        if (raw == null) {
            throw fieldError("body", "不能为空");
        }
        return guard(() -> new QuestionWrite(
                QuestionImportValidator.requireText("title", raw.title(), 200),
                QuestionImportValidator.requireText("jobFamily", raw.jobFamily(), 64)
                        .toUpperCase(Locale.ROOT),
                QuestionImportValidator.normalizeDimension(raw.dimension()),
                QuestionImportValidator.normalizeDifficulty(raw.difficulty()),
                QuestionImportValidator.sanitizeTags(raw.tags()),
                QuestionImportValidator.requireText("stem", raw.stem(), null),
                QuestionImportValidator.optionalText("referenceAnswer", raw.referenceAnswer(), null),
                scoringPoints(raw.scoringPoints()),
                QuestionImportValidator.optionalText("company", raw.company(), 120),
                QuestionImportValidator.optionalText("sourceUrl", raw.sourceUrl(), null),
                QuestionImportValidator.optionalText("sourceLicense", raw.sourceLicense(), 64),
                defaultLanguage(raw.language())));
    }

    private QuestionWrite normalizePatch(QuestionWrite raw) {
        if (raw == null) {
            throw fieldError("body", "不能为空");
        }
        return guard(() -> new QuestionWrite(
                raw.title() == null ? null : QuestionImportValidator.requireText("title", raw.title(), 200),
                raw.jobFamily() == null
                        ? null
                        : QuestionImportValidator.requireText("jobFamily", raw.jobFamily(), 64)
                                .toUpperCase(Locale.ROOT),
                raw.dimension() == null ? null : QuestionImportValidator.normalizeDimension(raw.dimension()),
                raw.difficulty() == null ? null : QuestionImportValidator.normalizeDifficulty(raw.difficulty()),
                raw.tags() == null ? null : QuestionImportValidator.sanitizeTags(raw.tags()),
                raw.stem() == null ? null : QuestionImportValidator.requireText("stem", raw.stem(), null),
                raw.referenceAnswer() == null
                        ? null
                        : QuestionImportValidator.optionalText("referenceAnswer", raw.referenceAnswer(), null),
                raw.scoringPoints() == null ? null : scoringPoints(raw.scoringPoints()),
                raw.company() == null ? null : QuestionImportValidator.optionalText("company", raw.company(), 120),
                raw.sourceUrl() == null
                        ? null
                        : QuestionImportValidator.optionalText("sourceUrl", raw.sourceUrl(), null),
                raw.sourceLicense() == null
                        ? null
                        : QuestionImportValidator.optionalText("sourceLicense", raw.sourceLicense(), 64),
                raw.language() == null ? null : QuestionImportValidator.optionalText("language", raw.language(), 16)));
    }

    private static String defaultLanguage(String language) {
        String normalized = QuestionImportValidator.optionalText("language", language, 16);
        return normalized == null ? DEFAULT_LANGUAGE : normalized;
    }

    private static List<Object> scoringPoints(List<Object> values) {
        return values == null
                ? List.of()
                : values.stream().filter(value -> value != null).toList();
    }

    private static String requireSourceRepo(String sourceRepo) {
        if (sourceRepo == null || sourceRepo.isBlank()) {
            throw fieldError("sourceRepo", "不能为空");
        }
        String stripped = sourceRepo.strip();
        if (stripped.length() > SOURCE_REPO_MAX_LENGTH) {
            throw fieldError("sourceRepo", SOURCE_REPO_MAX);
        }
        return stripped;
    }

    private static String normalizeStatusFilter(String status) {
        String value = blankToNull(status);
        if (value == null) {
            return null;
        }
        String normalized = value.toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(normalized)) {
            throw fieldError("status", "取值必须为 DRAFT/PUBLISHED/ARCHIVED");
        }
        return normalized;
    }

    private static String nullOrDimension(String dimension) {
        return blankToNull(dimension) == null ? null : QuestionImportValidator.normalizeDimension(dimension);
    }

    private static String nullOrDifficulty(String difficulty) {
        return blankToNull(difficulty) == null ? null : QuestionImportValidator.normalizeDifficulty(difficulty);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static Map<String, Object> summary(Question question) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("title", question.title());
        value.put("jobFamily", question.jobFamily());
        value.put("dimension", question.dimension());
        value.put("difficulty", question.difficulty());
        value.put("status", question.status());
        value.put("source", question.source());
        return value;
    }

    private void addError(List<Map<String, Object>> errors, int index, String field, String reason) {
        if (errors.size() >= MAX_ERRORS) {
            return;
        }
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("index", index);
        if (field != null) {
            error.put("field", field);
        }
        error.put("reason", reason);
        errors.add(error);
    }

    private void markBatchFailed(long batchId, int success, int failed, int skipped, List<Map<String, Object>> errors) {
        try {
            questionRepository.finishImportBatch(batchId, "FAILED", success, failed, skipped, toJson(errors));
        } catch (RuntimeException exception) {
            log.warn("导入批次 {} 标记 FAILED 失败：{}", batchId, exception.getClass().getSimpleName());
        }
    }

    private String toJson(List<Map<String, Object>> errors) {
        try {
            return objectMapper.writeValueAsString(errors == null ? List.of() : errors);
        } catch (JsonProcessingException exception) {
            return "[]";
        }
    }

    private static <T> T guard(Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (InvalidQuestionFieldException exception) {
            throw fieldError(exception.field(), exception.getMessage());
        }
    }

    private static BizException fieldError(String field, String message) {
        return new BizException(ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of(field, message)));
    }

    private static BizException stateConflict(Question question) {
        return new BizException(ErrorCode.STATE_CONFLICT, Map.of("status", question.status()));
    }
}
