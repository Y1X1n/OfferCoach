package com.offercoach.identity.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.Data;

/**
 * app_user 表映射。
 */
@Data
@TableName("app_user")
public class AppUserDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String email;

    private String phone;

    private String phoneHash;

    private String passwordHash;

    private String nickname;

    private String avatarUrl;

    private String role;

    private String status;

    private String timezone;

    private OffsetDateTime emailVerifiedAt;

    private OffsetDateTime lastLoginAt;

    private Integer failedLoginCount;

    private OffsetDateTime lockedUntil;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    private OffsetDateTime deletedAt;
}
