package com.offercoach.app;

import com.offercoach.identity.application.port.UserRepository;
import com.offercoach.identity.application.quota.QuotaService;
import com.offercoach.identity.domain.user.NewUser;
import com.offercoach.identity.domain.user.UserAccount;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 本地演示账号初始化；幂等，日志只输出邮箱，不输出密码。
 */
@Component
@ConditionalOnProperty(prefix = "oc.demo", name = "enabled", havingValue = "true")
public class DemoDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String ROLE_USER = "USER";
    private static final String PLAN_PRO = "PRO";
    private static final String DEFAULT_TIMEZONE = "Asia/Shanghai";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final QuotaService quotaService;
    private final String email;
    private final String password;
    private final String nickname;

    public DemoDataInitializer(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            QuotaService quotaService,
            @Value("${oc.demo.email:}") String email,
            @Value("${oc.demo.password:}") String password,
            @Value("${oc.demo.nickname:演示用户}") String nickname) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.quotaService = quotaService;
        this.email = email;
        this.password = password;
        this.nickname = nickname;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            log.warn("oc.demo.email 或 oc.demo.password 为空，跳过演示账号初始化");
            return;
        }
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        UserAccount existing = userRepository.findByEmail(normalizedEmail).orElse(null);
        if (existing != null) {
            // initializeQuota 幂等，可补齐历史运行中缺失的额度行。
            quotaService.initializeQuota(existing.id(), PLAN_PRO);
            log.info("演示账号已存在，跳过创建：{}", normalizedEmail);
            return;
        }

        NewUser newUser = new NewUser(
                normalizedEmail,
                passwordEncoder.encode(password),
                nickname == null || nickname.isBlank() ? "演示用户" : nickname.trim(),
                ROLE_USER,
                STATUS_ACTIVE,
                DEFAULT_TIMEZONE);
        UserAccount created;
        try {
            created = userRepository.insert(newUser);
        } catch (DuplicateKeyException ex) {
            log.info("演示账号已存在（并发创建），跳过：{}", normalizedEmail);
            return;
        }

        quotaService.initializeQuota(created.id(), PLAN_PRO);
        log.info("演示账号已创建：{}，套餐 {}", normalizedEmail, PLAN_PRO);
    }
}
