package com.gak.travelrecord.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gak.travelrecord.domain.TravelGroup;
import org.apache.ibatis.annotations.Mapper;

/** 出行分组持久化。 */
@Mapper
public interface TravelGroupMapper extends BaseMapper<TravelGroup> { }
