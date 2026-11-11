package com.gak.workbench.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdScalarSerializer;
import java.io.IOException;

/** 工作台版本是客户端需原样回传的小计数器，局部覆盖全局 Long 转字符串规则。 */
public class WorkbenchPreferenceVersionSerializer extends StdScalarSerializer<Long> {
    public WorkbenchPreferenceVersionSerializer() {
        super(Long.class);
    }

    @Override
    public void serialize(Long version, JsonGenerator generator, SerializerProvider provider) throws IOException {
        generator.writeNumber(version);
    }
}
