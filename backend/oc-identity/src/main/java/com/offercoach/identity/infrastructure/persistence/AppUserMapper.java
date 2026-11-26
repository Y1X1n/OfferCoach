package com.offercoach.identity.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * app_user 基础 CRUD。
 */
@Mapper
public interface AppUserMapper extends BaseMapper<AppUserDO> {

    /**
     * 清空注册指纹与个人邀请码（注销时调用）。
     *
     * <p>这三列没有映射到 {@link AppUserDO}，只能走原生 SQL。注册 IP 与设备指纹属于个人信息，
     * 个人邀请码是账号的可识别标识，注销后都不应继续保留。</p>
     */
    @Update(
            """
            UPDATE app_user
            SET signup_ip = NULL, signup_device_hash = NULL, invite_code = NULL, updated_at = now()
            WHERE id = #{id}
            """)
    int clearSignupFingerprint(@Param("id") long id);
}
