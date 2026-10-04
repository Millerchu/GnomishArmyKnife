package com.gak.personalbills.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gak.personalbills.domain.PersonalBill;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 个人账单 Mapper。
 */
@Mapper
public interface PersonalBillMapper extends BaseMapper<PersonalBill> {
    /** Lock ownership and source linkage while a bill is being mutated. */
    @Select("SELECT * FROM gak_personal_bill WHERE id = #{id} AND owner_user_id = #{ownerUserId} FOR UPDATE")
    PersonalBill selectOwnedForUpdate(@Param("id") Long id, @Param("ownerUserId") Long ownerUserId);
}
