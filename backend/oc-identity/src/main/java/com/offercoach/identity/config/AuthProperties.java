package com.offercoach.identity.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 认证相关配置，前缀 oc.auth；对应 oc-app application.yml。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "oc.auth")
public class AuthProperties {

    /** Access Token 有效期，默认 15 分钟。 */
    private Duration accessTokenTtl = Duration.ofMinutes(15);

    /** Refresh Token 有效期，默认 7 天。 */
    private Duration refreshTokenTtl = Duration.ofDays(7);

    /** Refresh 轮换宽限期：并发或多标签页在宽限期内重复提交旧 Token 时同族补发，超期按重放吊销。 */
    private Duration refreshGrace = Duration.ofSeconds(30);

    private Jwt jwt = new Jwt();

    private Cookie cookie = new Cookie();

    /** 设备指纹散列盐；留空时使用空盐，仅影响散列不可逆性。 */
    private String deviceSalt = "";

    /** 可信代理共享密钥；配置后仅当请求携带匹配的 X-Proxy-Secret 才采信 X-Real-IP。 */
    private String proxySecret = "";

    private DevVerifyCode devVerifyCode = new DevVerifyCode();

    private InviteCode inviteCode = new InviteCode();

    @Getter
    @Setter
    public static class Jwt {

        private String issuer = "offercoach";

        private String audience = "offercoach-web";

        private String keyId = "oc-local";

        /** PKCS#8 私钥 PEM 路径；留空时启动期生成临时密钥。 */
        private String privateKeyPath;

        /** X.509 公钥 PEM 路径；留空时启动期生成临时密钥。 */
        private String publicKeyPath;
    }

    @Getter
    @Setter
    public static class Cookie {

        private String name = "oc_rt";

        private boolean secure;

        private String sameSite = "Lax";

        private String path = "/api/v1/auth";
    }

    @Getter
    @Setter
    public static class DevVerifyCode {

        /** 本地开发开关；关闭时注册接口直接拒绝（未接入真实验证码服务）。 */
        private boolean enabled;

        private String code = "123456";
    }

    /**
     * 生产注册开关：未接入邮件/短信验证码前，用邀请码控制注册入口。
     */
    @Getter
    @Setter
    public static class InviteCode {

        private boolean enabled;

        private String code = "";
    }
}
