package com.gak.user.mapper.user;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gak.user.domain.user.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 用户 Mapper。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
    @Select("SELECT id FROM gak_user WHERE id = #{id} FOR UPDATE")
    Long lockUser(@Param("id") Long id);
}
