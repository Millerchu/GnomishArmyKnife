package com.gak.healthrecord.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gak.healthrecord.domain.HealthVisit;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 医院就诊记录 Mapper。
 */
@Mapper
public interface HealthVisitMapper extends BaseMapper<HealthVisit> {

    @Select("""
            SELECT EXISTS (
                SELECT 1 FROM gak_health_visit
                WHERE owner_user_id = #{ownerUserId}
                  AND (case_record_file_name = #{fileName} OR case_record_url = #{fileUrl})
            )
            """)
    boolean existsOwnedCaseRecordFile(@Param("ownerUserId") Long ownerUserId,
                                      @Param("fileName") String fileName,
                                      @Param("fileUrl") String fileUrl);
}
