package com.gak.healthrecord.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gak.healthrecord.domain.HealthReport;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 健康报告 Mapper。
 */
@Mapper
public interface HealthReportMapper extends BaseMapper<HealthReport> {

    @Select("""
            SELECT EXISTS (
                SELECT 1 FROM gak_user u
                WHERE u.id = #{userId}
                  AND
            """ + DentalCleaningMapper.HEALTH_APP_ACCESS + ")")
    boolean hasHealthAppAccess(@Param("userId") Long userId);

    @Select("""
            SELECT EXISTS (
                SELECT 1 FROM gak_health_report
                WHERE owner_user_id = #{ownerUserId}
                  AND (report_file_name = #{fileName} OR report_url = #{fileUrl})
            )
            """)
    boolean existsOwnedReportFile(@Param("ownerUserId") Long ownerUserId,
                                  @Param("fileName") String fileName,
                                  @Param("fileUrl") String fileUrl);
}
