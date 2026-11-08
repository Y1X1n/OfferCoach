package com.offercoach.app.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offercoach.common.error.BizException;
import com.offercoach.common.error.ErrorCode;
import com.offercoach.identity.application.quota.QuotaResult;
import com.offercoach.identity.application.quota.QuotaService;
import com.offercoach.identity.domain.quota.QuotaType;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {"oc.rate-limit.register-per-minute=100", "oc.rate-limit.login-per-minute=100"})
@AutoConfigureMockMvc
class IdentityM0IntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("offercoach")
            .withUsername("oc")
            .withPassword("oc_test_password");

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private QuotaService quotaService;

    @Test
    void registerLoginRefreshLogoutAndTokenRevocation() throws Exception {
        String email = "flow@offercoach.dev";
        MvcResult registered = register(email);
        String access1 = json(registered).get("accessToken").asText();
        Cookie refresh1 = registered.getResponse().getCookie("oc_rt");
        assertThat(refresh1).isNotNull();

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", bearer(access1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.planCode").value("FREE"));

        mockMvc.perform(get("/api/v1/quota").header("Authorization", bearer(access1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interviewCredits").value(3))
                .andExpect(jsonPath("$.resumeCredits").value(3))
                .andExpect(jsonPath("$.daily.sessionsUsed").value(0));

        MvcResult refreshed = mockMvc.perform(post("/api/v1/auth/refresh").cookie(refresh1))
                .andExpect(status().isOk())
                .andReturn();
        String access2 = json(refreshed).get("accessToken").asText();
        Cookie refresh2 = refreshed.getResponse().getCookie("oc_rt");
        assertThat(access2).isNotEqualTo(access1);
        assertThat(refresh2).isNotNull();
        assertThat(refresh2.getValue()).isNotEqualTo(refresh1.getValue());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", bearer(access2))
                        .cookie(refresh2))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", bearer(access2)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(refresh1))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_INVALID"));
    }

    @Test
    void loginFailuresAreUniformAndProfilePatchValidatesTimezone() throws Exception {
        String email = "profile@offercoach.dev";
        MvcResult registered = register(email);
        String access = json(registered).get("accessToken").asText();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("account", email, "password", "WrongPass1"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        mockMvc.perform(patch("/api/v1/users/me")
                        .header("Authorization", bearer(access))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("nickname", "新昵称", "timezone", "Asia/Tokyo"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("新昵称"))
                .andExpect(jsonPath("$.timezone").value("Asia/Tokyo"));

        mockMvc.perform(patch("/api/v1/users/me")
                        .header("Authorization", bearer(access))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("timezone", "Not/AZone"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details.fields.timezone").exists());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email, "password", "Passw0rd!", "nickname", "重复邮箱", "verifyCode", "123456"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void quotaChargeIsRowLockedAndIdempotent() throws Exception {
        String email = "quota@offercoach.dev";
        long userId = json(register(email)).get("userId").asLong();

        QuotaResult first =
                quotaService.charge(userId, QuotaType.INTERVIEW, 1, "SESSION_CHARGE", "SESSION", 1L, "idem-1");
        assertThat(first.replayed()).isFalse();
        assertThat(first.balanceAfter()).isEqualTo(2);

        QuotaResult replay =
                quotaService.charge(userId, QuotaType.INTERVIEW, 1, "SESSION_CHARGE", "SESSION", 1L, "idem-1");
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.balanceAfter()).isEqualTo(2);

        QuotaResult second =
                quotaService.charge(userId, QuotaType.INTERVIEW, 1, "SESSION_CHARGE", "SESSION", 2L, "idem-2");
        assertThat(second.balanceAfter()).isEqualTo(1);

        assertThatThrownBy(() ->
                        quotaService.charge(userId, QuotaType.INTERVIEW, 5, "SESSION_CHARGE", "SESSION", 3L, "idem-3"))
                .isInstanceOf(BizException.class)
                .satisfies(exception ->
                        assertThat(((BizException) exception).errorCode()).isEqualTo(ErrorCode.QUOTA_EXCEEDED));

        QuotaResult refunded =
                quotaService.refund(userId, QuotaType.INTERVIEW, 1, "SESSION_REFUND", "SESSION", 1L, "idem-4");
        assertThat(refunded.balanceAfter()).isEqualTo(2);

        assertThat(quotaService.snapshot(userId).interviewCredits()).isEqualTo(2);
        assertThat(quotaService.snapshot(userId).resumeCredits()).isEqualTo(3);
    }

    private MvcResult register(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "Passw0rd!",
                                "nickname", "测试用户",
                                "verifyCode", "123456"))))
                .andExpect(status().isCreated())
                .andReturn();
        assertThat(result.getResponse().getCookie("oc_rt")).isNotNull();
        return result;
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
