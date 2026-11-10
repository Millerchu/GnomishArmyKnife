package com.gak.systemlog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gak.systemlog.domain.SystemExceptionLog;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

/**
 * 系统异常日志持久化。
 */
@Mapper
public interface SystemExceptionLogMapper extends BaseMapper<SystemExceptionLog> {

    /** true 对应 SET LOCAL，事务完成后不污染连接池。 */
    @Select("SELECT set_config('statement_timeout', #{statementTimeout}, true)")
    String setCleanupStatementTimeout(@Param("statementTimeout") String statementTimeout);

    /** 显式 int4 重载隔离既有 bigint 锁空间。 */
    @Select("SELECT pg_try_advisory_xact_lock(CAST(#{namespace} AS int4), CAST(#{resource} AS int4))")
    boolean tryCleanupLock(@Param("namespace") int namespace, @Param("resource") int resource);

    /** 应用无表级 DELETE，删除仅由属主函数执行。 */
    @Select(value = "SELECT public.gak_purge_system_exception_log(#{cutoff}, #{limit})", affectData = true)
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    int purgeExpired(@Param("cutoff") LocalDateTime cutoff, @Param("limit") int limit);

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
