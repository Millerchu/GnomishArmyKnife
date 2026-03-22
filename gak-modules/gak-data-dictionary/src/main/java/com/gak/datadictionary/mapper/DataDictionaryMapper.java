package com.gak.datadictionary.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gak.datadictionary.domain.DataDictionary;
import org.apache.ibatis.annotations.Mapper;

/**
 * 数据字典主表 Mapper。
 */
@Mapper
public interface DataDictionaryMapper extends BaseMapper<DataDictionary> {
}
