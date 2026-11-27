package com.gak.permission.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gak.permission.domain.UserAppPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 用户应用授权 Mapper。
 */
@Mapper
public interface UserAppPermissionMapper extends BaseMapper<UserAppPermission> {

    @Select("""
            SELECT EXISTS (
                SELECT 1 FROM gak_user u
                JOIN gak_system_app a ON a.app_code = #{appCode} AND a.enabled = TRUE
                WHERE u.id = #{userId} AND u.enabled = TRUE AND UPPER(u.status) = 'ENABLED'
                AND (
                    EXISTS (
                        SELECT 1 FROM gak_user_app_permission p
                        WHERE p.user_id = u.id AND p.app_code = a.app_code AND p.granted = TRUE
                    )
                    OR (UPPER(u.role_code) = 'ADMIN' AND NOT EXISTS (
                        SELECT 1 FROM gak_user_app_permission p
                        JOIN gak_system_app granted_app ON granted_app.app_code = p.app_code AND granted_app.enabled = TRUE
                        WHERE p.user_id = u.id AND p.granted = TRUE
                    ))
                )
            )
            """)
    boolean isAllowed(@Param("userId") Long userId, @Param("appCode") String appCode);
}
