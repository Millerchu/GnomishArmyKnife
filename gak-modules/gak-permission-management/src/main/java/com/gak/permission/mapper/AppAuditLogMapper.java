package com.gak.permission.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gak.permission.domain.AppAuditLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 应用管理审计日志 Mapper。
 */
@Mapper
public interface AppAuditLogMapper extends BaseMapper<AppAuditLog> {
}
