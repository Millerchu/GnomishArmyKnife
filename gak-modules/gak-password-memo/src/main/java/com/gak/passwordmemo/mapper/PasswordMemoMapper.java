package com.gak.passwordmemo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gak.passwordmemo.domain.PasswordMemo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 密码备忘录 Mapper。
 */
@Mapper
public interface PasswordMemoMapper extends BaseMapper<PasswordMemo> {
}
