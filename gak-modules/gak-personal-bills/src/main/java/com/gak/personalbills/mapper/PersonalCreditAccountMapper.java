package com.gak.personalbills.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gak.personalbills.domain.PersonalCreditAccount;
import org.apache.ibatis.annotations.Mapper;
/** 个人授信账户持久化。 */
@Mapper
public interface PersonalCreditAccountMapper extends BaseMapper<PersonalCreditAccount> { }
