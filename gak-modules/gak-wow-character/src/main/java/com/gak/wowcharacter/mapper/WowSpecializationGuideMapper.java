package com.gak.wowcharacter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gak.wowcharacter.domain.WowSpecializationGuide;
import org.apache.ibatis.annotations.Mapper;

/**
 * WoW 赛季职业专精指南 Mapper。
 */
@Mapper
public interface WowSpecializationGuideMapper extends BaseMapper<WowSpecializationGuide> {
}
