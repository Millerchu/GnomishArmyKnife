package com.gak.wowcharacter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gak.wowcharacter.domain.WowCharacterKeybinding;
import org.apache.ibatis.annotations.Mapper;

/**
 * WoW 角色专精键位 Mapper。
 */
@Mapper
public interface WowCharacterKeybindingMapper extends BaseMapper<WowCharacterKeybinding> {
}
