package com.gak.personalbills.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gak.personalbills.domain.PersonalCreditAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 个人授信账户持久化。 */
@Mapper
public interface PersonalCreditAccountMapper extends BaseMapper<PersonalCreditAccount> {
    @Select("SELECT * FROM gak_personal_credit_account WHERE id = #{id} AND owner_user_id = #{owner} FOR UPDATE")
    PersonalCreditAccount selectOwnedForUpdate(@Param("id") Long id, @Param("owner") Long owner);
}
