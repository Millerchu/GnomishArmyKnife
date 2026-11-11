package com.gak.workbench.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 整份偏好替换入参；身份和 pref_key 均不属于可写字段。 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(using = WorkbenchPreferenceRequestDeserializer.class)
public record WorkbenchPreferenceRequest(
        @NotNull(message = "version 必填")
        @Min(value = 0, message = "version 不能为负数")
        @Max(value = Long.MAX_VALUE - 1, message = "version 超出范围") Long version,
        @NotNull(message = "schemaVersion 必填") Integer schemaVersion,
        @NotNull(message = "payload 必须为对象") JsonNode payload) {
}
