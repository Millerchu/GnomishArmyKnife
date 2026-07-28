package com.gak.requirementboard.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gak.requirementboard.domain.RequirementProgressLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 需求进度记录 Mapper。
 */
@Mapper
public interface RequirementProgressLogMapper extends BaseMapper<RequirementProgressLog> {
}
