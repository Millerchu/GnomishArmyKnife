package com.gak.permission.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gak.permission.domain.PermissionAuditLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 权限审计日志 Mapper。
 */
@Mapper
public interface PermissionAuditLogMapper extends BaseMapper<PermissionAuditLog> {
}
