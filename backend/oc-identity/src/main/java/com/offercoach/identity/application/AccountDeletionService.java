package com.offercoach.identity.application;

import com.offercoach.common.error.BizException;
import com.offercoach.common.error.ErrorCode;
import com.offercoach.identity.application.port.UserRepository;
import com.offercoach.identity.domain.user.UserAccount;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户自助注销：校验密码 → 软删并清空联系方式 → 吊销 Refresh 家族与 Access 版本。
 *
 * <p>额度流水、审计与支付订单保留主键关联，便于对账与法务留存；简历/回答/报告等业务数据
 * 当前不在本接口删除，由后续用户级联保留策略处理。</p>
 */
@Service
public class AccountDeletionService {

    private static final Logger log = LoggerFactory.getLogger(AccountDeletionService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final TokenEpochService tokenEpochService;

    public AccountDeletionService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            RefreshTokenService refreshTokenService,
            TokenEpochService tokenEpochService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.tokenEpochService = tokenEpochService;
    }

    @Transactional
    public void delete(long userId, String password) {
        UserAccount user = userRepository
                .findById(userId)
                .filter(UserAccount::isActive)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND));
        if (password == null || password.isEmpty()) {
            throw fieldError("password", "请输入密码");
        }
        if (!passwordEncoder.matches(password, user.passwordHash())) {
            throw fieldError("password", "密码不正确");
        }
        userRepository.softDelete(user.id(), OffsetDateTime.now(ZoneOffset.UTC));
        refreshTokenService.revokeAllForUser(user.id());
        tokenEpochService.bump(user.id());
        log.info("用户自助注销完成：userId={}", user.id());
    }

    private static BizException fieldError(String field, String message) {
        return new BizException(ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of(field, message)));
    }
}
