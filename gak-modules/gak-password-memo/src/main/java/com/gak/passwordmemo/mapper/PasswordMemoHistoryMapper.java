package com.gak.passwordmemo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gak.passwordmemo.domain.PasswordMemoHistory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 密码备忘录历史密码 Mapper。
 */
@Mapper
public interface PasswordMemoHistoryMapper extends BaseMapper<PasswordMemoHistory> {
}
