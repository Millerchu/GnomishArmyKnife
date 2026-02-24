package com.gak.worklog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gak.worklog.entity.WorkLogType;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工作日志类型关联表 Mapper。
 */
@Mapper
public interface WorkLogTypeMapper extends BaseMapper<WorkLogType> {
}
