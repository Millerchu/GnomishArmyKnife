package com.gak.aisubscription.mapper;
import org.apache.ibatis.annotations.*;
/** 应用授权与用户写锁。 */
@Mapper
public interface AiAccessMapper {
 @Select("SELECT id FROM gak_user WHERE id = #{owner} FOR UPDATE")
 Long lockOwner(Long owner);
 @Select("SELECT EXISTS (SELECT 1 FROM gak_user u JOIN gak_system_app a ON a.app_code = #{app} WHERE u.id = #{owner} AND u.enabled = TRUE AND u.status = 'ENABLED' AND a.enabled = TRUE AND (u.role_code = 'ADMIN' OR EXISTS (SELECT 1 FROM gak_user_app_permission p WHERE p.user_id = u.id AND p.app_code = a.app_code AND p.granted = TRUE)))")
 boolean hasAppAccess(@Param("owner") Long owner, @Param("app") String app);
}
