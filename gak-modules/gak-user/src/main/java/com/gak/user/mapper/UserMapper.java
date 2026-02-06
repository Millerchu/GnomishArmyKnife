package com.gak.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gak.user.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户表 Mapper。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}