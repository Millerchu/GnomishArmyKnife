package com.gak.personalbills.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gak.personalbills.domain.PersonalBudget;
import org.apache.ibatis.annotations.Mapper;

/**
 * 年度预算 Mapper。
 */
@Mapper
public interface PersonalBudgetMapper extends BaseMapper<PersonalBudget> {
}
