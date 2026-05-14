package com.gak.wowcharacter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gak.wowcharacter.domain.WowCharacterWeeklyVault;
import org.apache.ibatis.annotations.Mapper;

/**
 * WoW 角色每周低保记录 Mapper。
 */
@Mapper
public interface WowCharacterWeeklyVaultMapper extends BaseMapper<WowCharacterWeeklyVault> {
}
