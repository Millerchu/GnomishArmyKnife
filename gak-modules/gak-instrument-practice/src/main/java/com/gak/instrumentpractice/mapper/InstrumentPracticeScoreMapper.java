package com.gak.instrumentpractice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gak.instrumentpractice.domain.InstrumentPracticeScore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 曲谱数据访问及账号容量锁。
 */
@Mapper
public interface InstrumentPracticeScoreMapper extends BaseMapper<InstrumentPracticeScore> {

    @Select("SELECT id FROM gak_user WHERE id = #{ownerUserId} FOR UPDATE")
    Long lockOwner(@Param("ownerUserId") Long ownerUserId);
}
