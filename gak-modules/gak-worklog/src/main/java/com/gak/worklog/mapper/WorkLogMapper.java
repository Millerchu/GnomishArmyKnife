package com.gak.worklog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gak.worklog.entity.WorkLog;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 工作日志主表 Mapper。
 */
@Mapper
public interface WorkLogMapper extends BaseMapper<WorkLog> {

    /**
     * 在当前事务内串行化同一用户的工作日志写入，避免并发请求绕过每日人天上限。
     */
    @Select("SELECT 1 FROM (SELECT pg_advisory_xact_lock(#{userId})) AS work_log_lock")
    Integer lockUserWorkLogs(@Param("userId") Long userId);

    @Select({
            "<script>",
            "SELECT COUNT(*) FROM gak_work_log",
            "WHERE user_id = #{userId}",
            "AND log_date = #{logDate}",
            "AND project_code = #{projectCode}",
            "<if test='ignoreLogId != null'>AND id != #{ignoreLogId}</if>",
            "</script>"
    })
    Long countByUserDateAndProject(@Param("userId") Long userId,
                                   @Param("logDate") LocalDate logDate,
                                   @Param("projectCode") String projectCode,
                                   @Param("ignoreLogId") Long ignoreLogId);

    @Select({
            "<script>",
            "SELECT COALESCE(SUM(person_day), 0) FROM gak_work_log",
            "WHERE user_id = #{userId}",
            "AND log_date = #{logDate}",
            "<if test='ignoreLogId != null'>AND id != #{ignoreLogId}</if>",
            "</script>"
    })
    BigDecimal sumPersonDayByUserAndDate(@Param("userId") Long userId,
                                         @Param("logDate") LocalDate logDate,
                                         @Param("ignoreLogId") Long ignoreLogId);
}
