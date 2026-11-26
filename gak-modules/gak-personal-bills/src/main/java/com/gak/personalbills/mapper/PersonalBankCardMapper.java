package com.gak.personalbills.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gak.personalbills.domain.PersonalBankCard;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PersonalBankCardMapper extends BaseMapper<PersonalBankCard> {
    @Select("SELECT * FROM gak_personal_bank_card WHERE id = #{id} AND owner_user_id = #{owner} FOR UPDATE")
    PersonalBankCard selectOwnedForUpdate(@Param("id") Long id, @Param("owner") Long owner);

    @Select("""
            SELECT EXISTS (
                SELECT 1 FROM gak_user u
                JOIN gak_system_app a ON a.app_code = 'APP_PERSONAL_BILLS'
                WHERE u.id = #{owner} AND u.enabled = TRUE AND u.status = 'ENABLED' AND a.enabled = TRUE
                  AND (u.role_code = 'ADMIN' OR EXISTS (
                      SELECT 1 FROM gak_user_app_permission p
                      WHERE p.user_id = u.id AND p.app_code = a.app_code AND p.granted = TRUE
                  ))
            )
            """)
    boolean hasBillAccess(@Param("owner") Long owner);
}
