package com.gak.user.mapper.user;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gak.user.domain.user.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 Mapper。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
