package com.offercoach.pay.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 支付服务间通信配置（oc-app 与 oc-pay-service 共用）。
 *
 * <p>渠道商户密钥等敏感配置不在此处，见 oc-pay-service 的 {@code oc.pay.channels}。
 * 内部令牌为部署期共享密钥，未配置时服务间调用一律拒绝（fail-closed）。</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "oc.pay")
public class PayProperties {

    /** 服务间内部鉴权令牌；oc-app 与 oc-pay-service 必须一致。 */
    private String internalToken = "";

    /** 内部鉴权请求头名，固定便于 Nginx / 网关白名单。 */
    private String internalHeader = "X-OC-Internal-Token";

    /** 内部调用透传的用户 ID 请求头名。 */
    private String userIdHeader = "X-OC-User-Id";

    /** 跨服务 Trace 透传的请求 ID 请求头名。 */
    private String requestIdHeader = "X-OC-Request-Id";

    /** oc-pay-service 基础地址，供 oc-app 侧 PayGateway 使用。 */
    private String baseUrl = "http://127.0.0.1:8099";

    /** 建立连接超时。 */
    private Duration connectTimeout = Duration.ofSeconds(5);

    /** 单次内部调用超时。 */
    private Duration requestTimeout = Duration.ofSeconds(15);
}
