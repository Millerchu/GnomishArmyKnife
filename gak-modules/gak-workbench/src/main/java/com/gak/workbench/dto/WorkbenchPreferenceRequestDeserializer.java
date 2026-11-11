package com.gak.workbench.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.gak.workbench.exception.WorkbenchPreferenceInvalidException;
import java.io.IOException;

/** 版本字段禁止 Jackson 将小数、布尔或字符串强制转换成整数。 */
public class WorkbenchPreferenceRequestDeserializer extends JsonDeserializer<WorkbenchPreferenceRequest> {
    @Override
    public WorkbenchPreferenceRequest deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonNode envelope = parser.getCodec().readTree(parser);
        if (!envelope.isObject()) {
            throw new WorkbenchPreferenceInvalidException("请求体必须为对象");
        }
        JsonNode version = envelope.get("version");
        JsonNode schema = envelope.get("schemaVersion");
        if (version == null || !version.isIntegralNumber() || !version.canConvertToLong()) {
            throw new WorkbenchPreferenceInvalidException("version 必须为整数");
        }
        if (schema == null || !schema.isIntegralNumber() || !schema.canConvertToInt()) {
            throw new WorkbenchPreferenceInvalidException("schemaVersion 必须为整数");
        }
        return new WorkbenchPreferenceRequest(version.longValue(), schema.intValue(), envelope.get("payload"));
    }
}
