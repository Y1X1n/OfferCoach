package com.offercoach.admin.config;

import com.offercoach.identity.application.port.UserRepository;
import com.offercoach.identity.application.quota.QuotaService;
import com.offercoach.identity.domain.user.NewUser;
import com.offercoach.identity.domain.user.UserAccount;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 管理员账号初始化：显式开启时创建或提升为 ADMIN；日志不输出密码，可重复执行。
 */
@Component
@ConditionalOnProperty(prefix = "oc.admin", name = "enabled", havingValue = "true")
public class AdminDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminDataInitializer.class);
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String PLAN_PRO = "PRO";
    private static final String DEFAULT_TIMEZONE = "Asia/Shanghai";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final QuotaService quotaService;
    private final AdminProperties properties;

    public AdminDataInitializer(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            QuotaService quotaService,
            AdminProperties properties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.quotaService = quotaService;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        String email = properties.getEmail();
        String password = properties.getPassword();
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            log.warn("oc.admin.email 或 oc.admin.password 为空，跳过管理员初始化");
            return;
        }
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        UserAccount existing = userRepository.findByEmail(normalizedEmail).orElse(null);
        if (existing != null) {
            if (existing.deletedAt() != null) {
                log.warn("管理员邮箱已被软删账号占用，跳过：{}", normalizedEmail);
                return;
            }
            String passwordHash = existing.passwordHash();
            if (passwordHash == null || passwordHash.isBlank() || !passwordEncoder.matches(password, passwordHash)) {
                log.error("管理员邮箱已存在但凭证与配置不匹配，拒绝提升，请人工处理：{}", normalizedEmail);
                return;
            }
            userRepository.updateRole(existing.id(), ROLE_ADMIN);
            quotaService.initializeQuota(existing.id(), PLAN_PRO);
            if (!STATUS_ACTIVE.equals(existing.status())) {
                log.warn("管理员账号状态为 {}，请人工确认：{}", existing.status(), normalizedEmail);
            }
            log.info("管理员账号已存在，已确保 ADMIN 角色：{}", normalizedEmail);
            return;
        }
        UserAccount created;
        try {
            created = userRepository.insert(new NewUser(
                    normalizedEmail,
                    passwordEncoder.encode(password),
                    defaultValue(properties.getNickname()),
                    ROLE_ADMIN,
                    STATUS_ACTIVE,
                    DEFAULT_TIMEZONE));
        } catch (DuplicateKeyException exception) {
            log.info("管理员账号已存在（并发创建），跳过：{}", normalizedEmail);
            return;
        }
        quotaService.initializeQuota(created.id(), PLAN_PRO);
        log.info("管理员账号已创建：{}", normalizedEmail);
    }

    private static String defaultValue(String nickname) {
        return nickname == null || nickname.isBlank() ? "管理员" : nickname.trim();
    }
}
