package com.gak.datamigration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gak.datamigration.domain.DataMigrationTaskItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 迁移任务明细 Mapper。
 */
@Mapper
public interface DataMigrationTaskItemMapper extends BaseMapper<DataMigrationTaskItem> {
}
