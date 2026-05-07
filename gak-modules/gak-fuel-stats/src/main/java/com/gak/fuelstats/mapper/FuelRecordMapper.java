package com.gak.fuelstats.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gak.fuelstats.domain.FuelRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 加油记录 Mapper。
 */
@Mapper
public interface FuelRecordMapper extends BaseMapper<FuelRecord> {
}
