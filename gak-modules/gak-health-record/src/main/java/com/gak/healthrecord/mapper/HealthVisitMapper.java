package com.gak.healthrecord.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gak.healthrecord.domain.HealthVisit;
import org.apache.ibatis.annotations.Mapper;

/**
 * 医院就诊记录 Mapper。
 */
@Mapper
public interface HealthVisitMapper extends BaseMapper<HealthVisit> {
}
