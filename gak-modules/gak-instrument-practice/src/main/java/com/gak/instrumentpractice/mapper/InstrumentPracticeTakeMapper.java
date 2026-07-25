package com.gak.instrumentpractice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gak.instrumentpractice.domain.InstrumentPracticeTake;
import org.apache.ibatis.annotations.Mapper;

/**
 * 随身乐器录音数据访问接口。
 */
@Mapper
public interface InstrumentPracticeTakeMapper extends BaseMapper<InstrumentPracticeTake> {
}
