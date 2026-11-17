package com.offercoach.pay.service.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 支付渠道配置（商户密钥等敏感项）。
 *
 * <p>凭据来自部署期环境变量 / 挂载文件，不落库、不写日志；未启用或未配全时渠道按不可用处理。</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "oc.pay.channels")
public class PayChannelProperties {

    private Wechat wechat = new Wechat();

    private Alipay alipay = new Alipay();

    private Mock mock = new Mock();

    /**
     * 微信支付 v3（Native 扫码 / H5）。
     */
    @Getter
    @Setter
    public static class Wechat {
        private boolean enabled = false;
        private String appId = "";
        private String mchId = "";
        /** 商户 API 证书序列号。 */
        private String merchantSerialNo = "";
        /** 商户 API 私钥（PKCS#8）文件路径；与 privateKeyPem 二选一，路径优先。 */
        private String privateKeyPath = "";

        private String privateKeyPem = "";
        /** APIv3 密钥，用于回调 resource 解密。 */
        private String apiV3Key = "";
        /** 微信平台证书（PEM）路径，用于回调验签。 */
        private String platformCertPath = "";
        /** 微信平台公钥（PEM）路径；与平台证书二选一。 */
        private String platformPublicKeyPath = "";
        /** 微信平台公钥（PEM）内联，测试或未挂载文件时使用。 */
        private String platformPublicKeyPem = "";

        private String apiBaseUrl = "https://api.mch.weixin.qq.com";
        private String notifyPath = "/pay/notify/wechat";
    }

    /**
     * 支付宝电脑网站 / 扫码支付（alipay.trade.precreate）。
     */
    @Getter
    @Setter
    public static class Alipay {
        private boolean enabled = false;
        private String appId = "";
        /** 应用私钥（PKCS#8）文件路径；与 privateKeyPem 二选一，路径优先。 */
        private String privateKeyPath = "";

        private String privateKeyPem = "";
        /** 支付宝公钥（PEM）文件路径，用于回调验签。 */
        private String alipayPublicKeyPath = "";

        private String alipayPublicKeyPem = "";
        private String gatewayUrl = "https://openapi.alipay.com/gateway.do";
        private String notifyPath = "/pay/notify/alipay";
        private String returnPath = "/pay/return/alipay";
        private String signType = "RSA2";
    }

    /**
     * 本地联调用的模拟渠道；仅在显式开启且非生产环境可用。
     */
    @Getter
    @Setter
    public static class Mock {
        private boolean enabled = false;
        private String confirmPath = "/pay/mock";
    }
}
