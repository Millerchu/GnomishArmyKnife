package com.gak.fuelstats.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gak.fuelstats.domain.FuelRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 加油记录 Mapper。
 */
@Mapper
public interface FuelRecordMapper extends BaseMapper<FuelRecord> {
    /** 修改和删除串行化，防止源记录与来源账单并发写入后不一致。 */
    @Select("SELECT * FROM gak_fuel_record WHERE id = #{id} AND owner_user_id = #{ownerUserId} FOR UPDATE")
    FuelRecord selectOwnedForUpdate(@Param("id") Long id, @Param("ownerUserId") Long ownerUserId);
}
