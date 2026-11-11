package com.gak.workbench.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

/** PostgreSQL JSONB 以 OTHER 绑定，避免 varchar 写 jsonb 的类型错误。 */
public class JsonbTypeHandler extends BaseTypeHandler<JsonNode> {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Override
    public void setNonNullParameter(PreparedStatement statement, int index, JsonNode payload, JdbcType jdbcType)
            throws SQLException {
        statement.setObject(index, payload.toString(), Types.OTHER);
    }

    @Override
    public JsonNode getNullableResult(ResultSet result, String column) throws SQLException {
        return parse(result.getString(column));
    }

    @Override
    public JsonNode getNullableResult(ResultSet result, int index) throws SQLException {
        return parse(result.getString(index));
    }

    @Override
    public JsonNode getNullableResult(CallableStatement statement, int index) throws SQLException {
        return parse(statement.getString(index));
    }

    private JsonNode parse(String json) throws SQLException {
        if (json == null) {
            return null;
        }
        try {
            return JSON.readTree(json);
        } catch (JsonProcessingException exception) {
            // JSONB 可存放超出当前 Jackson 深度或数字长度限制的文档；保留行元数据供客户端覆盖。
            return null;
        }
    }
}
