package com.gak.worklog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gak.worklog.dto.UnfinishedWorkItemResponse;
import com.gak.worklog.entity.WorkLogItem;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 工作日志内容条目 Mapper。
 */
@Mapper
public interface WorkLogItemMapper extends BaseMapper<WorkLogItem> {

    /**
     * 按项目和条目内容保留最新状态，已在后续日志完成的内容不再作为快捷候选。
     */
    @Select({
            "SELECT latest.item_id AS id, latest.work_log_id, latest.log_date, latest.project_code,",
            "       latest.content AS work_item, latest.status",
            "FROM (",
            "    SELECT DISTINCT ON (log.project_code, BTRIM(item.content))",
            "           item.id AS item_id, item.work_log_id, log.log_date, log.project_code,",
            "           item.content, item.status, item.updated_at, log.updated_at AS log_updated_at",
            "    FROM gak_work_log_item item",
            "    JOIN gak_work_log log ON log.id = item.work_log_id",
            "    WHERE log.user_id = #{userId}",
            "      AND item.content IS NOT NULL",
            "      AND BTRIM(item.content) != ''",
            "    ORDER BY log.project_code, BTRIM(item.content), log.log_date DESC,",
            "             item.updated_at DESC, log.updated_at DESC, item.id DESC",
            ") latest",
            "WHERE latest.status = #{status}",
            "ORDER BY latest.log_date DESC, latest.item_id DESC",
            "LIMIT #{limit}"
    })
    List<UnfinishedWorkItemResponse> selectLatestByStatus(@Param("userId") Long userId,
                                                           @Param("status") String status,
                                                           @Param("limit") int limit);
}
