package com.gak.fuelstats.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gak.fuelstats.domain.FuelPriceSnapshot;
import org.apache.ibatis.annotations.Mapper;

/**
 * 油价快照 Mapper。
 */
@Mapper
public interface FuelPriceSnapshotMapper extends BaseMapper<FuelPriceSnapshot> {
}
