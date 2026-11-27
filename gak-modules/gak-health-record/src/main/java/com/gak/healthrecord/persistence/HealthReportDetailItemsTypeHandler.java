package com.gak.healthrecord.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gak.healthrecord.domain.HealthReportDetailItem;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Collections;
import java.util.List;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

/**
 * PostgreSQL JSONB 明细数组处理器，以 Types.OTHER 绑定避免 varchar 写入 jsonb 报错。
 */
public class HealthReportDetailItemsTypeHandler extends BaseTypeHandler<List<HealthReportDetailItem>> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final TypeReference<List<HealthReportDetailItem>> LIST_TYPE = new TypeReference<>() {
    };

    @Override
    public void setNonNullParameter(PreparedStatement statement, int index,
                                    List<HealthReportDetailItem> items, JdbcType jdbcType) throws SQLException {
        try {
            String json = OBJECT_MAPPER.writeValueAsString(items == null ? Collections.emptyList() : items);
            statement.setObject(index, json, Types.OTHER);
        } catch (JsonProcessingException exception) {
            throw new SQLException("序列化体检报告明细失败", exception);
        }
    }

    @Override
    public List<HealthReportDetailItem> getNullableResult(ResultSet resultSet, String columnName) throws SQLException {
        return parse(resultSet.getString(columnName));
    }

    @Override
    public List<HealthReportDetailItem> getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException {
        return parse(resultSet.getString(columnIndex));
    }

    @Override
    public List<HealthReportDetailItem> getNullableResult(CallableStatement statement, int columnIndex) throws SQLException {
        return parse(statement.getString(columnIndex));
    }

    private List<HealthReportDetailItem> parse(String json) throws SQLException {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            List<HealthReportDetailItem> parsed = OBJECT_MAPPER.readValue(json, LIST_TYPE);
            return parsed == null ? Collections.emptyList() : parsed;
        } catch (JsonProcessingException exception) {
            throw new SQLException("反序列化体检报告明细失败", exception);
        }
    }
}
