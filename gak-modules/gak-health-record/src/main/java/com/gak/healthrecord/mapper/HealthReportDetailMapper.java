package com.gak.healthrecord.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gak.healthrecord.domain.HealthReportDetail;
import com.gak.healthrecord.persistence.HealthReportDetailItemsTypeHandler;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 体检报告明细集合 Mapper。
 */
@Mapper
public interface HealthReportDetailMapper extends BaseMapper<HealthReportDetail> {

    @Select("""
            SELECT report_id, record_id, revision, items, updated_at
            FROM gak_health_report_detail
            WHERE report_id = #{reportId}
            """)
    @Results(id = "healthReportDetailMap", value = {
            @Result(column = "report_id", property = "reportId", id = true),
            @Result(column = "record_id", property = "recordId"),
            @Result(column = "revision", property = "revision"),
            @Result(column = "items", property = "items", typeHandler = HealthReportDetailItemsTypeHandler.class),
            @Result(column = "updated_at", property = "updatedAt")
    })
    HealthReportDetail findByReportId(@Param("reportId") Long reportId);

    @Select("""
            SELECT report_id, record_id, revision, items, updated_at
            FROM gak_health_report_detail
            WHERE record_id = #{recordId}
            """)
    @Results(value = {
            @Result(column = "report_id", property = "reportId", id = true),
            @Result(column = "record_id", property = "recordId"),
            @Result(column = "revision", property = "revision"),
            @Result(column = "items", property = "items", typeHandler = HealthReportDetailItemsTypeHandler.class),
            @Result(column = "updated_at", property = "updatedAt")
    })
    HealthReportDetail findByRecordId(@Param("recordId") Long recordId);

    @Select("""
            <script>
            SELECT report_id, record_id
            FROM gak_health_report_detail
            WHERE report_id IN
            <foreach collection="reportIds" item="id" open="(" separator="," close=")">
                #{id}
            </foreach>
            </script>
            """)
    List<HealthReportDetail> findLinksByReportIds(@Param("reportIds") List<Long> reportIds);

    @Select("""
            <script>
            SELECT report_id, record_id
            FROM gak_health_report_detail
            WHERE record_id IN
            <foreach collection="recordIds" item="id" open="(" separator="," close=")">
                #{id}
            </foreach>
            </script>
            """)
    List<HealthReportDetail> findLinksByRecordIds(@Param("recordIds") List<Long> recordIds);

    @Select("""
            SELECT report_id, record_id, revision, items, updated_at
            FROM gak_health_report_detail
            ORDER BY report_id ASC
            """)
    @Results(value = {
            @Result(column = "report_id", property = "reportId", id = true),
            @Result(column = "record_id", property = "recordId"),
            @Result(column = "revision", property = "revision"),
            @Result(column = "items", property = "items", typeHandler = HealthReportDetailItemsTypeHandler.class),
            @Result(column = "updated_at", property = "updatedAt")
    })
    List<HealthReportDetail> selectAllOrdered();

    @Insert("""
            INSERT INTO gak_health_report_detail (report_id, record_id, revision, items, updated_at)
            VALUES (
                #{reportId},
                #{recordId},
                #{revision},
                #{items,typeHandler=com.gak.healthrecord.persistence.HealthReportDetailItemsTypeHandler},
                #{updatedAt}
            )
            """)
    int insertDetail(HealthReportDetail detail);

    @Update("""
            UPDATE gak_health_report_detail
            SET record_id = #{detail.recordId},
                revision = #{detail.revision},
                items = #{detail.items,typeHandler=com.gak.healthrecord.persistence.HealthReportDetailItemsTypeHandler},
                updated_at = #{detail.updatedAt}
            WHERE report_id = #{detail.reportId} AND revision = #{expectedRevision}
            """)
    int updateDetailWithRevision(@Param("detail") HealthReportDetail detail,
                                 @Param("expectedRevision") Integer expectedRevision);
}
