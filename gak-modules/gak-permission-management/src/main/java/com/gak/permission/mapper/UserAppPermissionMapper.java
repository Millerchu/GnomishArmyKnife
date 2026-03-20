package com.gak.permission.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gak.permission.domain.UserAppPermission;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户应用授权 Mapper。
 */
@Mapper
public interface UserAppPermissionMapper extends BaseMapper<UserAppPermission> {
}
