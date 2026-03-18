package com.gak.wowcharacter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gak.wowcharacter.domain.WowCharacter;
import org.apache.ibatis.annotations.Mapper;

/**
 * WoW 角色 Mapper。
 */
@Mapper
public interface WowCharacterMapper extends BaseMapper<WowCharacter> {
}
