package com.gak.worklog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gak.worklog.entity.WorkLog;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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

    /**
     * 按项目和内容保留最新一条状态，避免已在后续完成的旧内容继续进入快捷候选。
     */
    @Select({
            "SELECT latest.id, latest.user_id, latest.log_date, latest.project_code,",
            "       latest.content, latest.work_status, latest.updated_at",
            "FROM (",
            "    SELECT DISTINCT ON (project_code, BTRIM(content))",
            "           id, user_id, log_date, project_code, content, work_status, updated_at",
            "    FROM gak_work_log",
            "    WHERE user_id = #{userId}",
            "      AND content IS NOT NULL",
            "      AND BTRIM(content) != ''",
            "    ORDER BY project_code, BTRIM(content), log_date DESC, updated_at DESC, id DESC",
            ") latest",
            "WHERE latest.work_status = #{workStatus}",
            "ORDER BY latest.log_date DESC, latest.updated_at DESC, latest.id DESC",
            "LIMIT #{limit}"
    })
    List<WorkLog> selectLatestWorkItemsByStatus(@Param("userId") Long userId,
                                                @Param("workStatus") String workStatus,
                                                @Param("limit") int limit);
}
