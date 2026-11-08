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

    long countAdmins();
}
