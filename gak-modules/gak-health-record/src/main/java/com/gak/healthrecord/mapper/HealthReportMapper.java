package com.gak.healthrecord.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gak.healthrecord.domain.HealthReport;
import org.apache.ibatis.annotations.Mapper;

/**
 * 健康报告 Mapper。
 */
@Mapper
public interface HealthReportMapper extends BaseMapper<HealthReport> {
}
