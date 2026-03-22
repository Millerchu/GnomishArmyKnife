package com.gak.datadictionary.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gak.datadictionary.domain.DataDictionaryUsage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 数据字典使用绑定 Mapper。
 */
@Mapper
public interface DataDictionaryUsageMapper extends BaseMapper<DataDictionaryUsage> {
}
