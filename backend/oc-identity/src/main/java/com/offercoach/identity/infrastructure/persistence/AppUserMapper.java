package com.offercoach.identity.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * app_user 基础 CRUD。
 */
@Mapper
public interface AppUserMapper extends BaseMapper<AppUserDO> {}
