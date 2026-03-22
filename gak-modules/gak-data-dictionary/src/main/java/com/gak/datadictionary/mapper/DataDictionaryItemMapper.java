package com.gak.datadictionary.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gak.datadictionary.domain.DataDictionaryItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 数据字典项 Mapper。
 */
@Mapper
public interface DataDictionaryItemMapper extends BaseMapper<DataDictionaryItem> {
}
