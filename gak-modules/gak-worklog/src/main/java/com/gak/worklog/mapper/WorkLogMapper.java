package com.gak.worklog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gak.worklog.entity.WorkLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工作日志主表 Mapper。
 */
@Mapper
public interface WorkLogMapper extends BaseMapper<WorkLog> {
}
