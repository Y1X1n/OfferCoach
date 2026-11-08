package com.offercoach.admin.application;

import com.offercoach.admin.application.model.AdminModels.AiCall;
import com.offercoach.admin.application.model.AdminModels.Paged;
import com.offercoach.admin.application.port.AdminAiCallRepository;
import com.offercoach.admin.application.support.PageQuery;
import com.offercoach.common.error.BizException;
import com.offercoach.common.error.ErrorCode;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * AI 调用日志只读查询。
 */
@Service
public class AdminAiCallService {

    private static final Set<String> STATUSES = Set.of("SUCCESS", "FAILED", "DEGRADED");

    private final AdminAiCallRepository aiCallRepository;

    public AdminAiCallService(AdminAiCallRepository aiCallRepository) {
        this.aiCallRepository = aiCallRepository;
    }

    public Paged<AiCall> list(int page, int size, String scene, String status, String userId) {
        PageQuery query = PageQuery.of(page, size);
        String sceneFilter = normalizeScene(scene);
        String statusFilter = normalizeStatus(status);
        Long userFilter = parseUserId(userId);
        List<AiCall> items =
                aiCallRepository.search(sceneFilter, statusFilter, userFilter, (int) query.offset(), query.size());
        long total = aiCallRepository.count(sceneFilter, statusFilter, userFilter);
        return new Paged<>(items, total, query.page(), query.size());
    }

    private static String normalizeScene(String scene) {
        String normalized = blankToNull(scene);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private static String normalizeStatus(String status) {
        String normalized = blankToNull(status);
        if (normalized == null) {
            return null;
        }
        String value = normalized.toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(value)) {
            throw fieldError("status", "取值必须为 SUCCESS/FAILED/DEGRADED");
        }
        return value;
    }

    private static Long parseUserId(String userId) {
        String value = blankToNull(userId);
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw fieldError("userId", "非数字 ID");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static BizException fieldError(String field, String message) {
        return new BizException(ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of(field, message)));
    }
}
