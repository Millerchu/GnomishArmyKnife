package com.gak.healthrecord.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gak.healthrecord.domain.HealthRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 健康指标记录 Mapper。
 */
@Mapper
public interface HealthRecordMapper extends BaseMapper<HealthRecord> {
}
