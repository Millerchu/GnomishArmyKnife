package com.gak.workbench.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.gak.workbench.serialization.WorkbenchPreferenceVersionSerializer;
import java.time.LocalDateTime;

/** version=0 仅表示云端无记录，不落库；小计数器须覆盖全局 Long 转字符串规则，以便客户端原样回传。 */
public record WorkbenchPreferenceVO(
        @JsonSerialize(using = WorkbenchPreferenceVersionSerializer.class) long version,
        int schemaVersion, JsonNode payload,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime updatedAt) {
    public static WorkbenchPreferenceVO empty() {
        return new WorkbenchPreferenceVO(0, 1, null, null);
    }
}
