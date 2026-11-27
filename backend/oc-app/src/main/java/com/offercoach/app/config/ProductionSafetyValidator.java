package com.offercoach.app.config;

import com.offercoach.identity.config.AuthProperties;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 启动期安全兜底：除本机开发（{@code local}）与测试（{@code test}）以外的任何 profile，
 * 只要安全开关没收紧就抛异常阻止启动，避免漏设环境变量导致"安全降级"。
 *
 * <p>此前这里用 {@code @Profile("prod")}，结果是**只有 profile 恰好等于 prod 才校验**：
 * 部署时漏了 {@code SPRING_PROFILES_ACTIVE}（或缺省成 {@code local}）就整套校验静默跳过，
 * 应用带着固定验证码、空代理密钥、非 Secure Cookie 正常启动。改成"默认校验、显式豁免"。</p>
 */
@Component
public class ProductionSafetyValidator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProductionSafetyValidator.class);

    /** 显式豁免的 profile：本机开发与自动化测试。 */
    private static final Set<String> EXEMPT_PROFILES = Set.of("local", "test");

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
        Set<String> profiles = effectiveProfiles(environment);
        if (profiles.stream().anyMatch(EXEMPT_PROFILES::contains)) {
            log.info("安全配置校验已跳过（开发/测试 profile={}）", profiles);
            return;
        }
        List<String> violations = new ArrayList<>();
        if (!isFalse(environment.getProperty("oc.demo.enabled"))) {
            violations.add("oc.demo.enabled 必须为 false（禁止创建演示账号）");
        }
        if (authProperties.getDevVerifyCode().isEnabled()) {
            violations.add("oc.auth.dev-verify-code.enabled 必须为 false（禁止固定验证码注册）");
        }
        if (authProperties.getProxySecret() == null
                || authProperties.getProxySecret().isBlank()) {
            violations.add("oc.auth.proxy-secret 必须配置（否则忽略全部代理头，所有用户共用同一限流桶）");
        }
        if (!authProperties.getCookie().isSecure()) {
            violations.add("oc.auth.cookie.secure 必须为 true（Refresh Cookie 仅经 HTTPS 下发）");
        }
        List<String> localOrigins = localOrigins(corsProperties.getAllowedOrigins());
        if (!localOrigins.isEmpty()) {
            violations.add("oc.cors.allowed-origins 不得包含本地地址：" + localOrigins);
        }
        if (!violations.isEmpty()) {
            throw new IllegalStateException("安全配置校验失败（profile=" + profiles + "）："
                    + String.join("；", violations)
                    + "；若只是本机开发，请显式设置 SPRING_PROFILES_ACTIVE=local");
        }
        log.info("安全配置校验通过（profile={}）", profiles);
    }

    /**
     * 生效的 profile 集合：有 active 用 active，否则用默认（{@code spring.profiles.default}，本仓库为 local）。
     */
    private static Set<String> effectiveProfiles(Environment environment) {
        Set<String> profiles = new LinkedHashSet<>(Arrays.asList(environment.getActiveProfiles()));
        if (profiles.isEmpty()) {
            profiles.addAll(Arrays.asList(environment.getDefaultProfiles()));
        }
        return profiles;
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
