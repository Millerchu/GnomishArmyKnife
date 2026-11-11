package com.gak.workbench.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gak.workbench.domain.UserPreference;
import com.gak.workbench.persistence.JsonbTypeHandler;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 所有偏好查询、写入都同时绑定用户和服务端偏好键。 */
@Mapper
public interface UserPreferenceMapper extends BaseMapper<UserPreference> {
    @Select("""
            SELECT id, user_id, pref_key, schema_version, payload, version, created_at, updated_at
            FROM gak_user_preference WHERE user_id = #{userId} AND pref_key = #{prefKey}
            """)
    @Results(value = {
            @Result(column = "payload", property = "payload", typeHandler = JsonbTypeHandler.class)
    })
    UserPreference find(@Param("userId") Long userId, @Param("prefKey") String prefKey);

    /** DO NOTHING 避免唯一键碰撞中止 PG 事务，确保随后仍能读到胜出者的文档。 */
    @Insert("""
            INSERT INTO gak_user_preference
                (id, user_id, pref_key, schema_version, payload, version, created_at, updated_at)
            VALUES (#{id}, #{userId}, #{prefKey}, #{schemaVersion},
                #{payload,typeHandler=com.gak.workbench.persistence.JsonbTypeHandler},
                #{version}, #{createdAt}, #{updatedAt})
            ON CONFLICT (user_id, pref_key) DO NOTHING
            """)
    int insertIfAbsent(UserPreference preference);

    @Update("""
            UPDATE gak_user_preference SET schema_version = #{schemaVersion},
                payload = #{payload,typeHandler=com.gak.workbench.persistence.JsonbTypeHandler},
                version = version + 1, updated_at = #{updatedAt}
            WHERE user_id = #{userId} AND pref_key = #{prefKey} AND version = #{version}
            """)
    int updateVersion(UserPreference preference);
}
