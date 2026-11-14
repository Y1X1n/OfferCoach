package com.offercoach.ai.tools.internal;

import com.offercoach.ai.tools.config.ToolsProperties;
import com.offercoach.ai.tools.error.ToolErrorKind;
import com.offercoach.ai.tools.error.ToolException;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Component;

/**
 * 服务间内部鉴权：只接受携带正确 {@code X-OC-Internal-Token} 的调用。
 *
 * <p>工具服务不暴露公网，令牌为部署期共享密钥；比较使用常量时间算法，避免时序侧信道。
 * 未配置令牌时拒绝所有调用（fail-closed），防止漏配导致裸奔。</p>
 */
@Component
public class InternalAuth {

    private final ToolsProperties properties;
    private final byte[] expectedToken;

    public InternalAuth(ToolsProperties properties) {
        this.properties = properties;
        String token = properties.getInternalToken();
        this.expectedToken = token == null || token.isBlank() ? null : token.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 校验内部请求；失败抛出 TOOL_INVALID_INPUT 以外的鉴权错误，由全局异常处理映射为 401。
     */
    public long requireUser(HttpServletRequest request) {
        if (expectedToken == null) {
            throw new ToolException(ToolErrorKind.NOT_CONFIGURED, "服务间令牌未配置");
        }
        String provided = request.getHeader(properties.getInternalHeader());
        if (provided == null || !matches(provided)) {
            throw new ToolException(ToolErrorKind.UNAUTHORIZED, "内部令牌无效");
        }
        String userId = request.getHeader(properties.getUserIdHeader());
        if (userId == null || userId.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(userId.trim());
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    public String requestId(HttpServletRequest request) {
        String requestId = request.getHeader(properties.getRequestIdHeader());
        return requestId == null ? "" : requestId;
    }

    private boolean matches(String provided) {
        byte[] candidate = provided.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(candidate, expectedToken);
    }
}
