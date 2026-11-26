package com.offercoach.admin.application.port;

import com.offercoach.admin.application.model.AdminModels.User;
import java.util.List;
import java.util.Optional;

/**
 * 管理端用户查询与状态更新；不暴露密码散列。
 */
public interface AdminUserRepository {

    List<User> search(String keyword, String status, int offset, int limit);

    long count(String keyword, String status);

    Optional<User> findById(long id);

    /**
     * 更新未软删账号状态；成功返回 1。
     */
    int updateStatus(long id, String status);

    int updateRole(long id, String role);

    int unlock(long id);

    int updatePassword(long id, String passwordHash);

    /**
     * 锁定全部管理员行并返回数量；用于「至少保留一个管理员」的检查-更新原子化。
     *
     * <p>必须在事务内调用。普通 COUNT(*) 与随后的 UPDATE 之间存在窗口，
     * 两个并发降级请求都可能读到 2 而各自放行，最终把管理员降到 0。</p>
     */
    long lockAdminsAndCount();
}
