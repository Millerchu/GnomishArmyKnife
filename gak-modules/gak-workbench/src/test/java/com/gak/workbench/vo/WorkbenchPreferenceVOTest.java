package com.gak.workbench.vo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gak.framework.config.JacksonLongToStringConfig;
import com.gak.framework.response.ApiResponse;
import com.gak.workbench.controller.WorkbenchPreferenceExceptionHandler;
import com.gak.workbench.exception.WorkbenchPreferenceConflictException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.*;

/** 加载实际 Spring Jackson 配置，避免独立 ObjectMapper 漏掉全局 Long 字符串序列化规则。 */
class WorkbenchPreferenceVOTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
            .withUserConfiguration(JacksonLongToStringConfig.class);

    @ParameterizedTest
    @ValueSource(longs = {0, 1, 5})
    void successAndStandaloneViewKeepVersionNumeric(long version) {
        runner.run(context -> {
            ObjectMapper mapper = context.getBean(ObjectMapper.class);
            WorkbenchPreferenceVO preference = preference(mapper, version);
            assertPreference(mapper.readTree(mapper.writeValueAsString(preference)), version);
            JsonNode response = mapper.readTree(mapper.writeValueAsString(ApiResponse.success(preference)));
            assertEquals("0", response.path("code").textValue());
            assertPreference(response.path("data"), version);
        });
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 5})
    void conflictResponseKeepsLatestVersionNumeric(long version) {
        runner.run(context -> {
            ObjectMapper mapper = context.getBean(ObjectMapper.class);
            var response = new WorkbenchPreferenceExceptionHandler().conflict(
                    new WorkbenchPreferenceConflictException(preference(mapper, version)));
            assertEquals(409, response.getStatusCode().value());
            JsonNode body = mapper.readTree(mapper.writeValueAsString(response.getBody()));
            assertEquals(WorkbenchPreferenceConflictException.CODE, body.path("code").textValue());
            assertPreference(body.path("data"), version);
        });
    }

    @Test
    void ordinaryLongValuesStillUseGlobalStringSerialization() {
        runner.run(context -> {
            ObjectMapper mapper = context.getBean(ObjectMapper.class);
            JsonNode response = mapper.readTree(mapper.writeValueAsString(ApiResponse.success(10L)));
            assertTrue(response.path("data").isTextual());
            assertEquals("10", response.path("data").textValue());
        });
    }

    private WorkbenchPreferenceVO preference(ObjectMapper mapper, long version) {
        if (version == 0) {
            return WorkbenchPreferenceVO.empty();
        }
        return new WorkbenchPreferenceVO(version, 1, mapper.createObjectNode(),
                LocalDateTime.of(2026, 11, 11, 12, 1, 2, 123456000));
    }

    private void assertPreference(JsonNode preference, long version) {
        assertTrue(preference.path("version").isIntegralNumber(), "version 必须是 JSON 整数节点");
        assertEquals(version, preference.path("version").longValue());
        assertEquals(1, preference.path("schemaVersion").intValue());
        if (version == 0) {
            assertTrue(preference.path("payload").isNull());
            assertFalse(preference.has("updatedAt"));
            return;
        }
        assertEquals("2026-11-11 12:01:02", preference.path("updatedAt").textValue());
    }
}
