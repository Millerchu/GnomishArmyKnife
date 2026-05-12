package com.gak.knowledgebase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gak.knowledgebase.domain.KnowledgeEntry;
import org.apache.ibatis.annotations.Mapper;

/**
 * 经验条目 Mapper。
 */
@Mapper
public interface KnowledgeEntryMapper extends BaseMapper<KnowledgeEntry> {
}
