package com.gak.fuelstats.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gak.fuelstats.domain.FuelVehicle;
import org.apache.ibatis.annotations.Mapper;

/**
 * 车辆档案数据访问层。
 */
@Mapper
public interface FuelVehicleMapper extends BaseMapper<FuelVehicle> {
}
