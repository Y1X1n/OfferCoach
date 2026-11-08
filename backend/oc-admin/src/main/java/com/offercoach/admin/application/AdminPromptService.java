package com.offercoach.admin.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.offercoach.admin.application.model.AdminModels.PromptTemplate;
import com.offercoach.admin.application.model.AdminModels.PromptVersion;
import com.offercoach.admin.application.port.AdminPromptRepository;
import com.offercoach.admin.application.support.ContentHasher;
import com.offercoach.admin.audit.AuditLogger;
import com.offercoach.common.error.BizException;
import com.offercoach.common.error.ErrorCode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Prompt 模板与版本管理：版本不可变，同一模板最多一个 PUBLISHED 版本。
 */
@Service
public class AdminPromptService {

    private static final Pattern CODE_PATTERN = Pattern.compile("[a-z][a-z0-9_]{0,63}");
    private static final Pattern VERSION_PATTERN = Pattern.compile("[A-Za-z0-9._-]{1,16}");
    private static final String ACTION_TEMPLATE_CREATE = "PROMPT_TEMPLATE_CREATE";
    private static final String ACTION_VERSION_CREATE = "PROMPT_VERSION_CREATE";
    private static final String ACTION_VERSION_PUBLISH = "PROMPT_VERSION_PUBLISH";
    private static final String TARGET_TEMPLATE = "PROMPT_TEMPLATE";
    private static final String TARGET_VERSION = "PROMPT_VERSION";

    private final AdminPromptRepository promptRepository;
    private final AuditLogger auditLogger;

    public AdminPromptService(AdminPromptRepository promptRepository, AuditLogger auditLogger) {
        this.promptRepository = promptRepository;
        this.auditLogger = auditLogger;
    }

    public List<PromptTemplate> listTemplates() {
        return promptRepository.listTemplates();
    }

    @Transactional
    public PromptTemplate createTemplate(long actorId, String code, String name, String scene, String description) {
        String normalizedCode = requireCode(code);
        String normalizedName = requireText("name", name, 120);
        String normalizedScene = requireText("scene", scene, 32).toUpperCase(Locale.ROOT);
        String normalizedDescription = optionalText(description);
        if (promptRepository.findTemplateByCode(normalizedCode).isPresent()) {
            throw new BizException(ErrorCode.STATE_CONFLICT, Map.of("code", normalizedCode));
        }
        long id;
        try {
            id = promptRepository.insertTemplate(
                    normalizedCode, normalizedName, normalizedScene, normalizedDescription);
        } catch (DuplicateKeyException exception) {
            throw new BizException(ErrorCode.STATE_CONFLICT, Map.of("code", normalizedCode));
        }
        PromptTemplate created = promptRepository
                .findTemplateByCode(normalizedCode)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("code", normalizedCode)));
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("code", created.code());
        after.put("name", created.name());
        after.put("scene", created.scene());
        auditLogger.log(actorId, ACTION_TEMPLATE_CREATE, TARGET_TEMPLATE, Long.toString(id), null, after);
        return created;
    }

    public List<PromptVersion> listVersions(String code) {
        String normalizedCode = requireCode(code);
        requireTemplate(normalizedCode);
        return promptRepository.listVersions(normalizedCode);
    }

    @Transactional
    public PromptVersion createVersion(
            long actorId, String code, String version, String content, JsonNode variablesSchema, JsonNode modelConfig) {
        String normalizedCode = requireCode(code);
        PromptTemplate template = requireTemplate(normalizedCode);
        String normalizedVersion = requireVersion(version);
        String normalizedContent = requireText("content", content, null);
        String variablesJson = requireJsonObject("variablesSchema", variablesSchema, "{}");
        String modelJson = requireJsonObject("modelConfig", modelConfig, "{}");
        String contentHash = ContentHasher.sha256Hex(normalizedContent);

        long id;
        try {
            id = promptRepository.insertVersion(
                    template.id(),
                    normalizedVersion,
                    normalizedContent,
                    variablesJson,
                    modelJson,
                    contentHash,
                    actorId);
        } catch (DuplicateKeyException exception) {
            throw new BizException(
                    ErrorCode.STATE_CONFLICT, Map.of("code", normalizedCode, "version", normalizedVersion));
        }
        promptRepository.touchTemplate(template.id());
        PromptVersion created = promptRepository
                .findVersion(normalizedCode, normalizedVersion)
                .orElseThrow(
                        () -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("version", normalizedVersion)));
        Map<String, Object> after = versionSummary(created);
        auditLogger.log(actorId, ACTION_VERSION_CREATE, TARGET_VERSION, Long.toString(id), null, after);
        return created;
    }

    /**
     * 发布：同一事务内先归档旧 PUBLISHED，再发布目标版本并写 published_at。
     */
    @Transactional
    public PromptVersion publish(long actorId, String code, String version) {
        String normalizedCode = requireCode(code);
        PromptTemplate template = requireTemplate(normalizedCode);
        String normalizedVersion = requireVersion(version);
        PromptVersion before = promptRepository
                .findVersion(normalizedCode, normalizedVersion)
                .orElseThrow(() -> new BizException(
                        ErrorCode.RESOURCE_NOT_FOUND, Map.of("code", normalizedCode, "version", normalizedVersion)));
        if ("PUBLISHED".equals(before.status())) {
            return before;
        }
        promptRepository.archivePublished(template.id());
        promptRepository.publish(before.id());
        promptRepository.touchTemplate(template.id());
        PromptVersion after = promptRepository
                .findVersion(normalizedCode, normalizedVersion)
                .orElseThrow(
                        () -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("version", normalizedVersion)));
        Map<String, Object> beforeAudit = versionSummary(before);
        Map<String, Object> afterAudit = versionSummary(after);
        auditLogger.log(
                actorId, ACTION_VERSION_PUBLISH, TARGET_VERSION, Long.toString(before.id()), beforeAudit, afterAudit);
        return after;
    }

    public PromptVersion active(String code) {
        String normalizedCode = requireCode(code);
        return promptRepository
                .findPublishedVersion(normalizedCode)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("code", normalizedCode)));
    }

    private PromptTemplate requireTemplate(String code) {
        return promptRepository
                .findTemplateByCode(code)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("code", code)));
    }

    private static Map<String, Object> versionSummary(PromptVersion version) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("templateCode", version.templateCode());
        value.put("version", version.version());
        value.put("status", version.status());
        value.put("contentHash", version.contentHash());
        return value;
    }

    private static String requireCode(String code) {
        String normalized = optionalText(code);
        if (normalized == null || !CODE_PATTERN.matcher(normalized).matches()) {
            throw fieldError("code", "需为 1-64 位小写字母、数字或下划线，且以字母开头");
        }
        return normalized;
    }

    private static String requireVersion(String version) {
        String normalized = optionalText(version);
        if (normalized == null || !VERSION_PATTERN.matcher(normalized).matches()) {
            throw fieldError("version", "需为 1-16 位字母、数字、点、下划线或短横线");
        }
        return normalized;
    }

    private static String requireJsonObject(String field, JsonNode value, String defaultValue) {
        if (value == null || value.isNull()) {
            return defaultValue;
        }
        if (!value.isObject()) {
            throw fieldError(field, "必须是 JSON 对象");
        }
        return value.toString();
    }

    private static String requireText(String field, String value, Integer maxLength) {
        String normalized = optionalText(value);
        if (normalized == null) {
            throw fieldError(field, "不能为空");
        }
        if (maxLength != null && normalized.length() > maxLength) {
            throw fieldError(field, "长度不能超过 " + maxLength);
        }
        return normalized;
    }

    private static String optionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }

    private static BizException fieldError(String field, String message) {
        return new BizException(ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of(field, message)));
    }
}
