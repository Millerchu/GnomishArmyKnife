package com.gak.datamigration.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.util.List;

/**
 * 迁移处理器查询辅助方法。
 */
public final class DataMigrationQuerySupport {

    private DataMigrationQuerySupport() {
    }

    public static <T> QueryWrapper<T> eqNullable(QueryWrapper<T> wrapper, String column, Object value) {
        if (value == null) {
            wrapper.isNull(column);
        } else {
            wrapper.eq(column, value);
        }
        return wrapper;
    }

    public static <T> List<T> emptyIfNull(List<T> list) {
        return list == null ? List.of() : list;
    }
}
