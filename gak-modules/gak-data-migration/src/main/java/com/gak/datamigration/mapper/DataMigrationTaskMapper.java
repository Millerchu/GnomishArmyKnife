package com.gak.datamigration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gak.datamigration.domain.DataMigrationTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 迁移任务 Mapper。
 */
@Mapper
public interface DataMigrationTaskMapper extends BaseMapper<DataMigrationTask> {
}
