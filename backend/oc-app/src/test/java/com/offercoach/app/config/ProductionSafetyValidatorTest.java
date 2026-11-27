package com.offercoach.app.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.offercoach.identity.config.AuthProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * 安全兜底必须"默认校验、显式豁免"。
 *
 * <p>此前是 {@code @Profile("prod")}：部署时漏设 {@code SPRING_PROFILES_ACTIVE}（或缺省成 local）
 * 整套校验静默跳过，应用带着固定验证码与空代理密钥正常启动。这里钉住新的语义：
 * 只有 local / test 豁免，其余任何 profile 都要校验通过。</p>
 */
class ProductionSafetyValidatorTest {

    private final AuthProperties auth = new AuthProperties();
    private final CorsProperties cors = new CorsProperties();

    private ProductionSafetyValidator validator(MockEnvironment environment) {
        environment.setProperty("oc.demo.enabled", "false");
        return new ProductionSafetyValidator(environment, auth, cors);
    }

    /** 把三项安全开关都收紧，用于构造"合规"环境。 */
    private void tighten() {
        auth.setProxySecret("proxy-secret");
        auth.getCookie().setSecure(true);
        auth.getDevVerifyCode().setEnabled(false);
        cors.setAllowedOrigins(List.of("https://app.example.com"));
    }

    @Test
    void localProfileIsExempt() {
        MockEnvironment environment = new MockEnvironment();
        environment.setDefaultProfiles("local");
        environment.setActiveProfiles("local");

        assertThatCode(() -> validator(environment).run(null)).doesNotThrowAnyException();
    }

    /** 未显式设置 profile 时按 spring.profiles.default=local 处理，同样是开发环境。 */
    @Test
    void defaultProfileWithoutActiveProfilesIsExempt() {
        MockEnvironment environment = new MockEnvironment();
        environment.setDefaultProfiles("local");

        assertThatCode(() -> validator(environment).run(null)).doesNotThrowAnyException();
    }

    @Test
    void testProfileIsExempt() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("test");

        assertThatCode(() -> validator(environment).run(null)).doesNotThrowAnyException();
    }

    @Test
    void prodWithTightenedSwitchesPasses() {
        tighten();
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        assertThatCode(() -> validator(environment).run(null)).doesNotThrowAnyException();
    }

    /**
     * 本次修复的核心：非 prod 的部署 profile 也必须校验，否则 staging/uat 会带着固定验证码上线。
     */
    @Test
    void stagingIsValidatedToo() {
        auth.setProxySecret("");
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("staging");

        assertThatThrownBy(() -> validator(environment).run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("proxy-secret");
    }

    @Test
    void fixedVerifyCodeIsRejectedOutsideLocal() {
        tighten();
        auth.getDevVerifyCode().setEnabled(true);
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        assertThatThrownBy(() -> validator(environment).run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dev-verify-code");
    }

    @Test
    void insecureCookieIsRejected() {
        tighten();
        auth.getCookie().setSecure(false);
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        assertThatThrownBy(() -> validator(environment).run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cookie.secure");
    }

    @Test
    void localCorsOriginIsRejected() {
        tighten();
        cors.setAllowedOrigins(List.of("https://app.example.com", "http://localhost:8082"));
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        assertThatThrownBy(() -> validator(environment).run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("localhost:8082");
    }
}
