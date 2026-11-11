package com.gak.workbench.persistence;

import java.sql.CallableStatement;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 不连接数据库，验证已存 JSONB 超出解析能力时仍能映射其余行字段。 */
class JsonbTypeHandlerTest {
    private final JsonbTypeHandler handler = new JsonbTypeHandler();

    @Test
    void unreadableStoredJsonFallsBackToNullForEveryReadMapping() throws Exception {
        ResultSet result = mock(ResultSet.class);
        CallableStatement statement = mock(CallableStatement.class);
        // PostgreSQL JSONB 可存放此类深层文档，Jackson 默认最多解析 1000 层。
        String deeplyNested = "[".repeat(1001) + "0" + "]".repeat(1001);
        when(result.getString("payload")).thenReturn(deeplyNested);
        when(result.getString(1)).thenReturn(deeplyNested);
        when(statement.getString(1)).thenReturn(deeplyNested);
        assertNull(handler.getNullableResult(result, "payload"));
        assertNull(handler.getNullableResult(result, 1));
        assertNull(handler.getNullableResult(statement, 1));
    }

    @Test
    void validJsonAndSqlNullStillMapNormally() throws Exception {
        ResultSet result = mock(ResultSet.class);
        when(result.getString("payload")).thenReturn("{\"cards\":[]}");
        assertTrue(handler.getNullableResult(result, "payload").path("cards").isArray());
        when(result.getString("payload")).thenReturn((String) null);
        assertNull(handler.getNullableResult(result, "payload"));
    }
}
