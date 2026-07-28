package com.gak.requirementboard.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gak.requirementboard.domain.Requirement;
import org.apache.ibatis.annotations.Mapper;

/**
 * 需求 Mapper。
 */
@Mapper
public interface RequirementMapper extends BaseMapper<Requirement> {
}
