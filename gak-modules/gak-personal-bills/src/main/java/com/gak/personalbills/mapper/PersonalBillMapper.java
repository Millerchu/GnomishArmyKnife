package com.gak.personalbills.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gak.personalbills.domain.PersonalBill;
import org.apache.ibatis.annotations.Mapper;

/**
 * 个人账单 Mapper。
 */
@Mapper
public interface PersonalBillMapper extends BaseMapper<PersonalBill> {
}
