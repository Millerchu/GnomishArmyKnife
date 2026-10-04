package com.gak.growthrecord.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gak.growthrecord.domain.GrowthChild;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;

/** GrowthChild 持久化。 */
@Mapper
public interface GrowthChildMapper extends BaseMapper<GrowthChild> {
    @Select("SELECT * FROM gak_growth_child WHERE id = #{childId} AND owner_user_id = #{owner} FOR UPDATE")
    GrowthChild lockOwnedChild(@Param("owner") Long owner, @Param("childId") Long childId);

    @Select("SELECT EXISTS (SELECT 1 FROM gak_user u JOIN gak_system_app a ON a.app_code = #{appCode} "
            + "WHERE u.id = #{owner} AND u.enabled = TRUE AND u.status = 'ENABLED' AND a.enabled = TRUE AND "
            + "(u.role_code = 'ADMIN' OR EXISTS (SELECT 1 FROM gak_user_app_permission p "
            + "WHERE p.user_id = u.id AND p.app_code = a.app_code AND p.granted = TRUE)))")
    boolean hasAppAccess(@Param("owner") Long owner, @Param("appCode") String appCode);
}


