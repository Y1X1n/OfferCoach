package com.offercoach.identity.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.offercoach.identity.application.port.UserRepository;
import com.offercoach.identity.domain.user.NewUser;
import com.offercoach.identity.domain.user.UserAccount;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 账号仓储的 MyBatis-Plus 实现：负责 DO 与领域视图互转。
 */
@Repository
public class UserRepositoryImpl implements UserRepository {

    private final AppUserMapper userMapper;

    public UserRepositoryImpl(AppUserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public Optional<UserAccount> findByEmail(String email) {
        AppUserDO user = userMapper.selectOne(
                Wrappers.<AppUserDO>lambdaQuery().eq(AppUserDO::getEmail, email).isNull(AppUserDO::getDeletedAt));
        return Optional.ofNullable(user).map(UserRepositoryImpl::toAccount);
    }

    @Override
    public Optional<UserAccount> findById(long id) {
        return Optional.ofNullable(userMapper.selectById(id)).map(UserRepositoryImpl::toAccount);
    }

    @Override
    public boolean existsByEmail(String email) {
        // 与部分唯一索引 uq_app_user_email_active 保持同一口径：软删行不占用邮箱。
        Long count = userMapper.selectCount(
                Wrappers.<AppUserDO>lambdaQuery().eq(AppUserDO::getEmail, email).isNull(AppUserDO::getDeletedAt));
        return count != null && count > 0;
    }

    @Override
    public UserAccount insert(NewUser user) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        AppUserDO entity = new AppUserDO();
        entity.setEmail(user.email());
        entity.setPasswordHash(user.passwordHash());
        entity.setNickname(user.nickname());
        entity.setRole(user.role());
        entity.setStatus(user.status());
        entity.setTimezone(user.timezone());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        userMapper.insert(entity);
        return toAccount(entity);
    }

    @Override
    public void updateLastLogin(long id, OffsetDateTime at) {
        userMapper.update(
                null,
                Wrappers.<AppUserDO>lambdaUpdate()
                        .eq(AppUserDO::getId, id)
                        .set(AppUserDO::getLastLoginAt, at)
                        .set(AppUserDO::getUpdatedAt, at));
    }

    @Override
    public void updateProfile(long id, String nickname, String timezone, String avatarUrl, OffsetDateTime at) {
        LambdaUpdateWrapper<AppUserDO> update =
                Wrappers.<AppUserDO>lambdaUpdate().eq(AppUserDO::getId, id).set(AppUserDO::getUpdatedAt, at);
        if (nickname != null) {
            update.set(AppUserDO::getNickname, nickname);
        }
        if (timezone != null) {
            update.set(AppUserDO::getTimezone, timezone);
        }
        if (avatarUrl != null) {
            update.set(AppUserDO::getAvatarUrl, avatarUrl);
        }
        userMapper.update(null, update);
    }

    @Override
    public List<UserAccount> search(String keyword, String status, int offset, int limit) {
        return userMapper
                .selectList(searchWrapper(keyword, status)
                        .orderByDesc(AppUserDO::getCreatedAt)
                        .orderByDesc(AppUserDO::getId)
                        .last("LIMIT " + limit + " OFFSET " + offset))
                .stream()
                .map(UserRepositoryImpl::toAccount)
                .toList();
    }

    @Override
    public long countSearch(String keyword, String status) {
        Long count = userMapper.selectCount(searchWrapper(keyword, status));
        return count == null ? 0 : count;
    }

    @Override
    public void updateStatus(long id, String status) {
        userMapper.update(
                null,
                Wrappers.<AppUserDO>lambdaUpdate()
                        .eq(AppUserDO::getId, id)
                        .set(AppUserDO::getStatus, status)
                        .set(AppUserDO::getUpdatedAt, OffsetDateTime.now(ZoneOffset.UTC)));
    }

    @Override
    public void updateRole(long id, String role) {
        userMapper.update(
                null,
                Wrappers.<AppUserDO>lambdaUpdate()
                        .eq(AppUserDO::getId, id)
                        .set(AppUserDO::getRole, role)
                        .set(AppUserDO::getUpdatedAt, OffsetDateTime.now(ZoneOffset.UTC)));
    }

    private static LambdaQueryWrapper<AppUserDO> searchWrapper(String keyword, String status) {
        LambdaQueryWrapper<AppUserDO> wrapper =
                Wrappers.<AppUserDO>lambdaQuery().isNull(AppUserDO::getDeletedAt);
        if (keyword != null && !keyword.isBlank()) {
            String value = keyword.strip();
            wrapper.and(query -> query.like(AppUserDO::getEmail, value).or().like(AppUserDO::getNickname, value));
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(AppUserDO::getStatus, status.strip().toUpperCase());
        }
        return wrapper;
    }

    private static UserAccount toAccount(AppUserDO user) {
        return new UserAccount(
                user.getId(),
                user.getEmail(),
                user.getPasswordHash(),
                user.getNickname(),
                user.getAvatarUrl(),
                user.getRole(),
                user.getStatus(),
                user.getTimezone(),
                user.getCreatedAt(),
                user.getLastLoginAt(),
                user.getDeletedAt());
    }
}
