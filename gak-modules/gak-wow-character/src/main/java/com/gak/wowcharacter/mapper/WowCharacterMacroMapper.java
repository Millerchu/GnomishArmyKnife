package com.gak.wowcharacter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gak.wowcharacter.domain.WowCharacterMacro;
import org.apache.ibatis.annotations.Mapper;

/**
 * WoW 角色专用宏 Mapper。
 */
@Mapper
public interface WowCharacterMacroMapper extends BaseMapper<WowCharacterMacro> {
}
