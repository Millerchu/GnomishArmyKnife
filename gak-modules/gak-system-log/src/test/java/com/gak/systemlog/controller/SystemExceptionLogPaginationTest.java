package com.gak.systemlog.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gak.framework.exception.GlobalExceptionHandler;
import com.gak.framework.exception.SystemExceptionCapture;
import com.gak.framework.response.PagedResult;
import com.gak.systemlog.service.SystemExceptionLogService;
import com.gak.user.service.user.TokenService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 使用真实 MVC 绑定和校验，验证分页错误在进入业务服务前符合契约。 */
class SystemExceptionLogPaginationTest {

    private SystemExceptionLogService logService;
    private MockMvc mvc;

    @BeforeEach
    void createMvc() {
        logService = mock(SystemExceptionLogService.class);
        TokenService tokens = mock(TokenService.class);
        when(tokens.requireCurrentUserId(any())).thenReturn(1L);
        when(logService.page(eq(1L), any())).thenReturn(new PagedResult<>(List.of(), 0L));
        GlobalExceptionHandler global = new GlobalExceptionHandler(
                new StaticListableBeanFactory().getBeanProvider(SystemExceptionCapture.class));
        mvc = MockMvcBuilders.standaloneSetup(new SystemExceptionLogController(logService, tokens))
                .setControllerAdvice(new SystemExceptionLogExceptionHandler(), global)
                .build();
    }

    @ParameterizedTest
    @CsvSource({"pageNo,0", "pageNo,-1", "pageNo,1000001",
            "pageSize,0", "pageSize,-1", "pageSize,101"})
    void rejectsInvalidPaginationWithContractCode(String parameter, String value) throws Exception {
        mvc.perform(get("/system/logs").param(parameter, value))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SYSTEM_LOG_QUERY_INVALID"))
                .andExpect(jsonPath("$.message").value("分页参数不合法"));
        verifyNoInteractions(logService);
    }

    @ParameterizedTest
    @CsvSource({"1,1", "1000000,100"})
    void acceptsPaginationBoundaries(String pageNo, String pageSize) throws Exception {
        mvc.perform(get("/system/logs").param("pageNo", pageNo).param("pageSize", pageSize))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void preservesOtherValidationCodeAndMessage() throws Exception {
        mvc.perform(get("/system/logs").param("keyword", "x".repeat(81)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"))
                .andExpect(jsonPath("$.message").value("关键字长度不能超过 80"));
        verifyNoInteractions(logService);
    }

    @ParameterizedTest
    @CsvSource({"pageNo,invalid", "pageSize,invalid", "occurredFrom,invalid"})
    void preservesBindingErrorCode(String parameter, String value) throws Exception {
        mvc.perform(get("/system/logs").param(parameter, value))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"));
        verifyNoInteractions(logService);
    }

    @Test
    void preservesDetailTypeMismatchCode() throws Exception {
        mvc.perform(get("/system/logs/invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"))
                .andExpect(jsonPath("$.message").value("系统日志查询参数格式错误"));
        verifyNoInteractions(logService);
    }
}
