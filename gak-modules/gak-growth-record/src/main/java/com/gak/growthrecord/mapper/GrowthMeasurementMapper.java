package com.gak.growthrecord.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gak.growthrecord.domain.GrowthMeasurement;
import org.apache.ibatis.annotations.Mapper;

/** GrowthMeasurement 持久化。 */
@Mapper
public interface GrowthMeasurementMapper extends BaseMapper<GrowthMeasurement> {
}


