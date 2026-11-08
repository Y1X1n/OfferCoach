package com.offercoach.identity.application.port;

import com.offercoach.identity.domain.user.NewUser;
import com.offercoach.identity.domain.user.UserAccount;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 账号仓储端口。
 *
 * <p>Application 层只依赖本接口，不直接接触 MyBatis Mapper；返回值不包含持久化 DO。
 * 密码散列仅供认证校验，任何层都不得向 API 或日志暴露。</p>
 */
public interface UserRepository {

    /**
     * 按邮箱查找未软删账号。
     */
    Optional<UserAccount> findByEmail(String email);

    /**
     * 按主键查找账号；软删账号也会返回，由调用方通过 {@link UserAccount#isActive()} 判定。
     */
    Optional<UserAccount> findById(long id);

    /**
     * 邮箱是否已占用；包含软删账号，与数据库唯一约束口径一致。
     */
    boolean existsByEmail(String email);

    /**
     * 新增账号，返回落库后的领域视图。
     */
    UserAccount insert(NewUser user);

    /**
     * 记录最近登录时间。
     */
    void updateLastLogin(long id, OffsetDateTime at);

    /**
     * 更新资料字段，仅更新非 null 值，并刷新更新时间。
     */
    void updateProfile(long id, String nickname, String timezone, String avatarUrl, OffsetDateTime at);

    /**
     * 管理端检索账号；keyword 为空时不加条件，status 为空时返回全部状态。
     */
    List<UserAccount> search(String keyword, String status, int offset, int limit);

    long countSearch(String keyword, String status);

    /**
     * 管理端更新账号状态（ACTIVE / DISABLED / LOCKED / DELETED）。
     */
    void updateStatus(long id, String status);

    /**
     * 管理端调整账号角色（USER / SUPPORT / ADMIN）。
     */
    void updateRole(long id, String role);
}
