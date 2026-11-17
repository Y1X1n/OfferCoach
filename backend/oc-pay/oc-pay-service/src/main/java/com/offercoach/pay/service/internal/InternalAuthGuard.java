package com.offercoach.pay.service.internal;

import com.offercoach.pay.config.PayProperties;
import com.offercoach.pay.internal.InternalTokens;
import com.offercoach.pay.service.error.PayErrorKind;
import com.offercoach.pay.service.error.PayException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * 内部调用鉴权：仅接受携带正确 {@code X-OC-Internal-Token} 的请求。
 *
 * <p>令牌为部署期共享密钥，常量时间比较；未配置时拒绝所有调用（fail-closed）。</p>
 */
@Component
public class InternalAuthGuard {

    private final PayProperties properties;

    public InternalAuthGuard(PayProperties properties) {
        this.properties = properties;
    }

    /**
     * 校验内部令牌；失败抛 401。
     */
    public void require(HttpServletRequest request) {
        String provided = request.getHeader(properties.getInternalHeader());
        if (!InternalTokens.matches(properties.getInternalToken(), provided)) {
            throw new PayException(PayErrorKind.UNAUTHENTICATED, "内部令牌无效");
        }
    }
}
