package com.offercoach.app.config;

import com.offercoach.identity.config.AuthProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * prod profile 启动期安全兜底：任一安全开关未收紧即抛异常阻止启动，
 * 避免漏设环境变量导致生产环境“安全降级”。
 */
@Component
@Profile("prod")
public class ProductionSafetyValidator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProductionSafetyValidator.class);

    private static final String PROD_PROFILE = "prod";
    private static final List<String> LOCAL_HOST_MARKERS = List.of("localhost", "127.0.0.1");

    private final Environment environment;
    private final AuthProperties authProperties;
    private final CorsProperties corsProperties;

    public ProductionSafetyValidator(
            Environment environment, AuthProperties authProperties, CorsProperties corsProperties) {
        this.environment = environment;
        this.authProperties = authProperties;
        this.corsProperties = corsProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> violations = new ArrayList<>();
        if (!isFalse(environment.getProperty("oc.demo.enabled"))) {
            violations.add("oc.demo.enabled 必须为 false（禁止生产创建演示账号）");
        }
        if (authProperties.getDevVerifyCode().isEnabled()) {
            violations.add("oc.auth.dev-verify-code.enabled 必须为 false（禁止固定验证码注册）");
        }
        if (authProperties.getProxySecret() == null
                || authProperties.getProxySecret().isBlank()) {
            violations.add("oc.auth.proxy-secret 必须配置（生产仅信任携带 X-Proxy-Secret 的代理头）");
        }
        if (!authProperties.getCookie().isSecure()) {
            violations.add("oc.auth.cookie.secure 必须为 true（Refresh Cookie 仅经 HTTPS 下发）");
        }
        List<String> localOrigins = localOrigins(corsProperties.getAllowedOrigins());
        if (!localOrigins.isEmpty()) {
            violations.add("oc.cors.allowed-origins 不得包含本地地址：" + localOrigins);
        }
        if (!violations.isEmpty()) {
            throw new IllegalStateException("生产安全配置校验失败：" + String.join("；", violations));
        }
        log.info("生产安全配置校验通过（profile={}）", PROD_PROFILE);
    }

    private static boolean isFalse(String value) {
        return value != null && "false".equalsIgnoreCase(value.trim());
    }

    private static List<String> localOrigins(List<String> allowedOrigins) {
        List<String> local = new ArrayList<>();
        if (allowedOrigins == null) {
            return local;
        }
        for (String origin : allowedOrigins) {
            if (origin == null) {
                continue;
            }
            String normalized = origin.trim().toLowerCase(Locale.ROOT);
            boolean isLocal = LOCAL_HOST_MARKERS.stream().anyMatch(normalized::contains);
            if (isLocal) {
                local.add(origin);
            }
        }
        return local;
    }
}
