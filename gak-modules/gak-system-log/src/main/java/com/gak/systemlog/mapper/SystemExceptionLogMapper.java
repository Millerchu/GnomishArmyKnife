package com.gak.systemlog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gak.systemlog.domain.SystemExceptionLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 系统异常日志持久化。
 */
@Mapper
public interface SystemExceptionLogMapper extends BaseMapper<SystemExceptionLog> {

    /**
     * 应用处于启用状态，且该用户被明确授予此应用。
     */
    @Select("""
            SELECT EXISTS (
                SELECT 1
                FROM gak_system_app app
                WHERE app.app_code = #{appCode}
                  AND app.enabled = TRUE
                  AND EXISTS (
                      SELECT 1
                      FROM gak_user_app_permission permission
                      WHERE permission.user_id = #{userId}
                        AND permission.app_code = app.app_code
                        AND permission.granted = TRUE
                  )
            )
            """)
    boolean hasEnabledGrant(@Param("userId") Long userId, @Param("appCode") String appCode);
}
