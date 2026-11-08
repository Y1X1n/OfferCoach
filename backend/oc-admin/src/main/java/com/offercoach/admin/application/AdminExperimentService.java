package com.offercoach.admin.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.offercoach.admin.application.model.AdminModels.Experiment;
import com.offercoach.admin.application.model.AdminModels.Paged;
import com.offercoach.admin.application.model.AdminModels.PromptTemplate;
import com.offercoach.admin.application.port.AdminExperimentRepository;
import com.offercoach.admin.application.port.AdminPromptRepository;
import com.offercoach.admin.application.support.PageQuery;
import com.offercoach.admin.audit.AuditLogger;
import com.offercoach.common.error.BizException;
import com.offercoach.common.error.ErrorCode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A/B 实验最小 CRUD；只维护配置与状态，不接入运行时分流。
 */
@Service
public class AdminExperimentService {

    private static final Set<String> STATUSES = Set.of("DRAFT", "RUNNING", "PAUSED", "COMPLETED", "ROLLED_BACK");
    private static final String ACTION_CREATE = "EXPERIMENT_CREATE";
    private static final String ACTION_STATUS = "EXPERIMENT_STATUS_UPDATE";
    private static final String TARGET = "EXPERIMENT";

    private final AdminExperimentRepository experimentRepository;
    private final AdminPromptRepository promptRepository;
    private final AuditLogger auditLogger;

    public AdminExperimentService(
            AdminExperimentRepository experimentRepository,
            AdminPromptRepository promptRepository,
            AuditLogger auditLogger) {
        this.experimentRepository = experimentRepository;
        this.promptRepository = promptRepository;
        this.auditLogger = auditLogger;
    }

    public Paged<Experiment> list(int page, int size) {
        PageQuery query = PageQuery.of(page, size);
        List<Experiment> items = experimentRepository.list((int) query.offset(), query.size());
        return new Paged<>(items, experimentRepository.count(), query.page(), query.size());
    }

    @Transactional
    public Experiment create(
            long actorId,
            String templateCode,
            String name,
            Integer trafficPercent,
            JsonNode variants,
            JsonNode guardrails) {
        String code = requireText("templateCode", templateCode, 64);
        PromptTemplate template = promptRepository
                .findTemplateByCode(code)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("templateCode", code)));
        String experimentName = requireText("name", name, 120);
        int traffic = trafficPercent == null ? 10 : trafficPercent;
        if (traffic < 0 || traffic > 100) {
            throw fieldError("trafficPercent", "取值范围为 0-100");
        }
        if (variants == null || !variants.isArray() || variants.isEmpty()) {
            throw fieldError("variants", "至少需要一个变体");
        }
        String guardrailsJson = toJsonObject(guardrails, "{}");
        long id = experimentRepository.insert(
                template.id(), experimentName, "DRAFT", traffic, variants.toString(), guardrailsJson);
        Experiment created = experimentRepository
                .findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("id", id)));
        Map<String, Object> after = summary(created);
        auditLogger.log(actorId, ACTION_CREATE, TARGET, Long.toString(id), null, after);
        return created;
    }

    @Transactional
    public Experiment updateStatus(long actorId, long id, String status) {
        String normalized = normalizeStatus(status);
        Experiment before = experimentRepository
                .findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("id", id)));
        if (normalized.equals(before.status())) {
            return before;
        }
        boolean setStart = "RUNNING".equals(normalized);
        boolean setEnd = "COMPLETED".equals(normalized) || "ROLLED_BACK".equals(normalized);
        experimentRepository.updateStatus(id, normalized, setStart, setEnd);
        Experiment after = experimentRepository
                .findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("id", id)));
        Map<String, Object> beforeAudit = Map.of("status", before.status());
        Map<String, Object> afterAudit = Map.of("status", after.status());
        auditLogger.log(actorId, ACTION_STATUS, TARGET, Long.toString(id), beforeAudit, afterAudit);
        return after;
    }

    private static String normalizeStatus(String status) {
        String normalized = status == null ? null : status.strip().toUpperCase(Locale.ROOT);
        if (normalized == null || !STATUSES.contains(normalized)) {
            throw fieldError("status", "仅支持 DRAFT/RUNNING/PAUSED/COMPLETED/ROLLED_BACK");
        }
        return normalized;
    }

    private static String toJsonObject(JsonNode value, String defaultValue) {
        if (value == null || value.isNull()) {
            return defaultValue;
        }
        if (!value.isObject()) {
            throw fieldError("guardrails", "必须是 JSON 对象");
        }
        return value.toString();
    }

    private static String requireText(String field, String value, int maxLength) {
        String normalized = value == null ? null : value.strip();
        if (normalized == null || normalized.isEmpty()) {
            throw fieldError(field, "不能为空");
        }
        if (normalized.length() > maxLength) {
            throw fieldError(field, "长度不能超过 " + maxLength);
        }
        return normalized;
    }

    private static Map<String, Object> summary(Experiment experiment) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("templateCode", experiment.templateCode());
        value.put("name", experiment.name());
        value.put("status", experiment.status());
        value.put("trafficPercent", experiment.trafficPercent());
        return value;
    }

    private static BizException fieldError(String field, String message) {
        return new BizException(ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of(field, message)));
    }
}
