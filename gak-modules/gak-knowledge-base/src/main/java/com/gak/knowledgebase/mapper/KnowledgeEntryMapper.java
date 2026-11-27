package com.gak.knowledgebase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gak.knowledgebase.domain.KnowledgeEntry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;

/**
 * 经验条目 Mapper。
 */
@Mapper
public interface KnowledgeEntryMapper extends BaseMapper<KnowledgeEntry> {
    @Select("""
            SELECT first_id FROM (
                (SELECT id AS first_id, 0 AS edge FROM gak_knowledge_entry WHERE status = 'PUBLISHED' ORDER BY id LIMIT 1)
                UNION ALL
                (SELECT id AS first_id, 1 AS edge FROM gak_knowledge_entry WHERE status = 'PUBLISHED' ORDER BY id DESC LIMIT 1)
            ) bounds ORDER BY edge
            """)
    List<Long> selectPublishedBounds();
}
