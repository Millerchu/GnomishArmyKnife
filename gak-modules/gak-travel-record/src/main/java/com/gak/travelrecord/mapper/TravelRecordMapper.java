package com.gak.travelrecord.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gak.travelrecord.domain.TravelRecord;
import org.apache.ibatis.annotations.*;

/** 行程持久化；用户行锁使该用户的行程、分组、关联账单写入串行化。 */
@Mapper
public interface TravelRecordMapper extends BaseMapper<TravelRecord> {
    @Select("SELECT id FROM gak_user WHERE id = #{userId} FOR UPDATE")
    Long lockOwner(@Param("userId") Long userId);

    @Select("SELECT EXISTS (SELECT 1 FROM gak_user u JOIN gak_system_app a ON a.app_code = #{appCode} "
            + "WHERE u.id = #{userId} AND u.enabled = TRUE AND u.status = 'ENABLED' AND a.enabled = TRUE AND "
            + "(u.role_code = 'ADMIN' OR EXISTS (SELECT 1 FROM gak_user_app_permission p "
            + "WHERE p.user_id = u.id AND p.app_code = a.app_code AND p.granted = TRUE)))")
    boolean hasAppAccess(@Param("userId") Long userId, @Param("appCode") String appCode);
}
