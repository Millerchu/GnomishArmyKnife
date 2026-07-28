package com.gak.requirementboard.mapper;

import com.gak.requirementboard.vo.RequirementAppOptionVO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 需求所属应用的只读查询 Mapper。
 */
@Mapper
public interface RequirementAppMapper {

    @Select("""
            SELECT app_code AS "appCode", app_name AS "appName"
            FROM gak_system_app
            WHERE enabled = TRUE
            ORDER BY sort_no ASC, id ASC
            """)
    List<RequirementAppOptionVO> selectEnabledApps();

    @Select("""
            SELECT app_code AS "appCode", app_name AS "appName"
            FROM gak_system_app
            WHERE app_code = #{appCode} AND enabled = TRUE
            """)
    RequirementAppOptionVO selectEnabledAppByCode(@Param("appCode") String appCode);
}
