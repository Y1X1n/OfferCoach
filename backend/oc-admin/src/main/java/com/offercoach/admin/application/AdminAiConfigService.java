package com.offercoach.admin.application;

import com.offercoach.admin.audit.AuditLogger;
import com.offercoach.ai.AiProvider;
import com.offercoach.ai.AiRuntimeConfigService;
import com.offercoach.ai.AiRuntimeConfigService.AiRuntimeSnapshot;
import com.offercoach.ai.AiUnavailableException;
import com.offercoach.ai.OpenAiCompatibleProvider;
import com.offercoach.common.error.BizException;
import com.offercoach.common.error.ErrorCode;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 管理端 AI 运行时配置：在线读写、审计与连通性探测；审计与响应均不含 API Key 明文。
 */
@Service
public class AdminAiConfigService {

    private static final String ACTION_UPDATE = "AI_CONFIG_UPDATE";
    private static final String TARGET_TYPE = "AI_RUNTIME_CONFIG";
    private static final String TARGET_ID = "1";
    private static final Duration TEST_TIMEOUT = Duration.ofSeconds(15);
    private static final String TEST_SYSTEM_PROMPT = "只回复 OK";
    private static final String TEST_USER_PROMPT = "ping";

    private final AiRuntimeConfigService configService;
    private final OpenAiCompatibleProvider provider;
    private final AuditLogger auditLogger;

    public AdminAiConfigService(
            AiRuntimeConfigService configService, OpenAiCompatibleProvider provider, AuditLogger auditLogger) {
        this.configService = configService;
        this.provider = provider;
        this.auditLogger = auditLogger;
    }

    public AiRuntimeSnapshot snapshot() {
        return configService.snapshot();
    }

    public AiRuntimeSnapshot update(
            long actorId,
            boolean enabled,
            String baseUrl,
            String model,
            String smallModel,
            String apiKey,
            boolean clearApiKey) {
        Map<String, Object> before = auditView(configService.snapshot());
        AiRuntimeSnapshot after =
                configService.update(actorId, enabled, baseUrl, model, smallModel, apiKey, clearApiKey);
        auditLogger.log(actorId, ACTION_UPDATE, TARGET_TYPE, TARGET_ID, before, auditView(after));
        return after;
    }

    /**
     * 极小请求探测当前配置；失败统一转为 AI_UNAVAILABLE（503）。
     */
    public AiProvider.AiResult test() {
        try {
            return provider.probe(TEST_SYSTEM_PROMPT, TEST_USER_PROMPT, TEST_TIMEOUT);
        } catch (AiUnavailableException exception) {
            String reason = exception.getMessage() == null ? "AI 服务暂不可用" : exception.getMessage();
            throw new BizException(ErrorCode.AI_UNAVAILABLE, Map.of("reason", reason));
        }
    }

    private static Map<String, Object> auditView(AiRuntimeSnapshot snapshot) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("enabled", snapshot.enabled());
        value.put("baseUrl", snapshot.baseUrl());
        value.put("model", snapshot.model());
        value.put("smallModel", snapshot.smallModel());
        value.put("hasApiKey", snapshot.hasApiKey());
        return value;
    }
}
