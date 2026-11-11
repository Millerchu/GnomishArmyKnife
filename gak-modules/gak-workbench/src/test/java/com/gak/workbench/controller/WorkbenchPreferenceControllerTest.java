package com.gak.workbench.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gak.framework.exception.BusinessException;
import com.gak.framework.exception.GlobalExceptionHandler;
import com.gak.framework.exception.SystemExceptionCapture;
import com.gak.user.service.user.TokenService;
import com.gak.user.domain.user.User;
import com.gak.user.mapper.user.UserMapper;
import com.gak.workbench.domain.UserPreference;
import com.gak.workbench.mapper.UserPreferenceMapper;
import com.gak.workbench.service.WorkbenchPayloadNormalizer;
import com.gak.workbench.service.WorkbenchPreferenceService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 真实 MVC + TokenService + Service，持久化使用 mock；不启动后端、不连接数据库。 */
class WorkbenchPreferenceControllerTest {
    private static final String PATH = "/workbench/preferences";
    private static final String INVALID = "WORKBENCH_PREFERENCE_INVALID";
    private UserPreferenceMapper mapper;
    private UserMapper userMapper;
    private MockMvc mvc;
    private String tokenA;
    private String tokenB;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void setup() {
        mapper = mock(UserPreferenceMapper.class);
        userMapper = mock(UserMapper.class);
        when(userMapper.selectById(anyLong())).thenReturn(new User());
        var tokens = new TokenService();
        tokenA = "Bearer " + tokens.issueToken(10L);
        tokenB = "Bearer " + tokens.issueToken(20L);
        var global = new GlobalExceptionHandler(
                new StaticListableBeanFactory().getBeanProvider(SystemExceptionCapture.class));
        var service = new WorkbenchPreferenceService(mapper, new WorkbenchPayloadNormalizer(), userMapper);
        mvc = MockMvcBuilders.standaloneSetup(new WorkbenchPreferenceController(tokens, service))
                .setControllerAdvice(new WorkbenchPreferenceExceptionHandler(), new WorkbenchPreferenceBodyAdvice(), global)
                .build();
    }

    @Test
    void ignoresOtherUserAndPrefKeyInBodyAndQuery() throws Exception {
        when(mapper.insertIfAbsent(any())).thenAnswer(invocation -> {
            UserPreference row = invocation.getArgument(0);
            assertEquals(10L, row.getUserId());
            assertEquals("workbench", row.getPrefKey());
            return 1;
        });
        mvc.perform(put(PATH).header("Authorization", tokenA).contentType("application/json").content("""
                {"version":0,"schemaVersion":1,"payload":{},"userId":20,"user_id":20,"pref_key":"other"}
                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(1));
        mvc.perform(get(PATH).header("Authorization", tokenB).param("userId", "10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(0))
                .andExpect(jsonPath("$.data.payload").isEmpty());
        verify(mapper).find(20L, "workbench");
        verify(mapper, never()).find(10L, "workbench");
    }

    @Test
    void conflictReturns409WithLatestAndDoesNotChangeBusinessException400() throws Exception {
        UserPreference latest = new UserPreference();
        latest.setSchemaVersion(1);
        latest.setVersion(5L);
        latest.setPayload(json.createObjectNode());
        latest.setUpdatedAt(LocalDateTime.of(2026, 11, 11, 12, 0));
        when(mapper.find(10L, "workbench")).thenReturn(latest);
        mvc.perform(put(PATH).header("Authorization", tokenA).contentType("application/json")
                .content("{\"version\":1,\"schemaVersion\":1,\"payload\":{}}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WORKBENCH_PREFERENCE_VERSION_CONFLICT"))
                .andExpect(jsonPath("$.data.version").value(5))
                .andExpect(jsonPath("$.data.schemaVersion").value(1))
                .andExpect(jsonPath("$.data.payload.dock.collapsed").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("未登录"))));
        when(mapper.find(10L, "workbench")).thenThrow(new BusinessException("OTHER_ERROR", "其他业务错误"));
        mvc.perform(get(PATH).header("Authorization", tokenA))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("OTHER_ERROR"));
    }

    @Test
    void requiresAuthenticationForBothMethodsAndExpiredToken() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("401"));
        mvc.perform(get(PATH).header("Authorization", "Bearer expired")).andExpect(status().isUnauthorized());
        mvc.perform(put(PATH).contentType("application/json")
                .content("{\"version\":0,\"schemaVersion\":1,\"payload\":{}}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(mapper);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{", "[]", "null",
            "{\"version\":null,\"schemaVersion\":1,\"payload\":{}}",
            "{\"version\":-1,\"schemaVersion\":1,\"payload\":{}}",
            "{\"version\":0.9,\"schemaVersion\":1,\"payload\":{}}",
            "{\"version\":\"0\",\"schemaVersion\":1,\"payload\":{}}",
            "{\"version\":0,\"schemaVersion\":1.2,\"payload\":{}}",
            "{\"version\":0,\"schemaVersion\":2,\"payload\":{}}",
            "{\"version\":0,\"schemaVersion\":0,\"payload\":{}}",
            "{\"version\":9223372036854775807,\"schemaVersion\":1,\"payload\":{}}",
            "{\"version\":0,\"schemaVersion\":1,\"payload\":null}",
            "{\"version\":0,\"schemaVersion\":1,\"payload\":[]}"})
    void invalidRequestsReturnStable400Code(String body) throws Exception {
        mvc.perform(put(PATH).header("Authorization", tokenA).contentType("application/json").content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(INVALID));
        verifyNoInteractions(mapper);
    }

    @Test
    void unsupportedOrMissingContentTypeReturns415AndMalformedJsonRemains400() throws Exception {
        String body = "{\"version\":0,\"schemaVersion\":1,\"payload\":{}}";
        mvc.perform(put(PATH).header("Authorization", tokenA).contentType("text/plain").content(body))
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.code").value(INVALID));
        mvc.perform(put(PATH).header("Authorization", tokenA).content(body))
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.code").value(INVALID));
        mvc.perform(put(PATH).header("Authorization", tokenA).contentType("application/json").content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(INVALID));
        verifyNoInteractions(mapper, userMapper);
    }

    @Test
    void deletedUserWithOldTokenReturns401ForGetAndPut() throws Exception {
        when(userMapper.selectById(10L)).thenReturn(null);
        mvc.perform(get(PATH).header("Authorization", tokenA))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("401"))
                .andExpect(jsonPath("$.message").value("当前登录用户不存在"));
        mvc.perform(put(PATH).header("Authorization", tokenA).contentType("application/json")
                        .content("{\"version\":0,\"schemaVersion\":1,\"payload\":{}}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("401"))
                .andExpect(jsonPath("$.message").value("当前登录用户不存在"));
        verifyNoInteractions(mapper);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"cards\":[{\"id\":\"removed\"}],\"dock\":{\"collapsed\":1}}", "[]", "null", "2"})
    void storedInvalidPayloadStillReturnsVersionInGetAndConflict(String payload) throws Exception {
        UserPreference latest = new UserPreference();
        latest.setVersion(5L);
        latest.setUpdatedAt(LocalDateTime.of(2026, 11, 11, 12, 0));
        latest.setPayload(json.readTree(payload));
        when(mapper.find(10L, "workbench")).thenReturn(latest);
        for (int schemaVersion : new int[]{1, 2}) {
            latest.setSchemaVersion(schemaVersion);
            mvc.perform(get(PATH).header("Authorization", tokenA))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").isNumber())
                    .andExpect(jsonPath("$.data.version").value(5))
                    .andExpect(jsonPath("$.data.schemaVersion").value(schemaVersion))
                    .andExpect(jsonPath("$.data.updatedAt").exists());
            mvc.perform(put(PATH).header("Authorization", tokenA).contentType("application/json")
                            .content("{\"version\":1,\"schemaVersion\":1,\"payload\":{}}"))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.data.version").isNumber())
                    .andExpect(jsonPath("$.data.version").value(5))
                    .andExpect(jsonPath("$.data.schemaVersion").value(schemaVersion))
                    .andExpect(jsonPath("$.data.updatedAt").exists());
        }
    }

    @Test
    void limitsNormalizedUtf8PayloadAndRawRequestIncludingMissingLength() throws Exception {
        ObjectNode envelope = json.createObjectNode().put("version", 0).put("schemaVersion", 1);
        var order = envelope.putObject("payload").putObject("dock").putArray("order");
        for (int i = 0; i < 100; i++) {
            order.add("中".repeat(62) + i);
        }
        mvc.perform(put(PATH).header("Authorization", tokenA).contentType("application/json").content(envelope.toString()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(INVALID));
        String huge = "{\"version\":0,\"schemaVersion\":1,\"payload\":{},\"unknown\":\"" + "a".repeat(65536) + "\"}";
        mvc.perform(put(PATH).header("Authorization", tokenA).contentType("application/json").content(huge)
                        .with(request -> { request.addHeader("Transfer-Encoding", "chunked"); return request; }))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(INVALID));
        verifyNoInteractions(mapper);
    }
}
