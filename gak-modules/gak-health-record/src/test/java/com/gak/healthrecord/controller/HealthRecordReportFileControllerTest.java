package com.gak.healthrecord.controller;

import com.gak.attachment.service.AttachmentService;
import com.gak.framework.exception.GlobalExceptionHandler;
import com.gak.framework.exception.SystemExceptionCapture;
import com.gak.healthrecord.mapper.HealthRecordMapper;
import com.gak.healthrecord.mapper.HealthReportDetailMapper;
import com.gak.healthrecord.mapper.HealthReportMapper;
import com.gak.healthrecord.mapper.HealthVisitMapper;
import com.gak.healthrecord.service.HealthRecordService;
import com.gak.healthrecord.service.HealthReportDetailService;
import com.gak.healthrecord.service.HealthVisitBillSyncService;
import com.gak.user.domain.user.User;
import com.gak.user.mapper.user.UserMapper;
import com.gak.user.service.user.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 健康报告兼容文件下载接口鉴权测试。
 */
class HealthRecordReportFileControllerTest {

    @TempDir
    Path tempDir;

    private HealthReportMapper healthReportMapper;
    private HealthVisitMapper healthVisitMapper;
    private UserMapper userMapper;
    private TokenService tokenService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws Exception {
        HealthRecordMapper healthRecordMapper = mock(HealthRecordMapper.class);
        healthVisitMapper = mock(HealthVisitMapper.class);
        healthReportMapper = mock(HealthReportMapper.class);
        userMapper = mock(UserMapper.class);
        AttachmentService attachmentService = mock(AttachmentService.class);
        HealthVisitBillSyncService visitBillSyncService = mock(HealthVisitBillSyncService.class);
        tokenService = new TokenService(userMapper, Duration.ofHours(24), Duration.ofHours(2), 100);

        Files.writeString(tempDir.resolve("report-1.pdf"), "mock-pdf");

        HealthReportDetailMapper healthReportDetailMapper = mock(HealthReportDetailMapper.class);
        HealthReportDetailService detailService = mock(HealthReportDetailService.class);
        HealthRecordService service = new HealthRecordService(
                healthRecordMapper,
                healthVisitMapper,
                healthReportMapper,
                healthReportDetailMapper,
                userMapper,
                attachmentService,
                visitBillSyncService,
                tempDir.toString(),
                "/api/health-records/report-files/"
        );
        HealthRecordController controller = new HealthRecordController(service, detailService, tokenService);
        GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler(
                new StaticListableBeanFactory().getBeanProvider(SystemExceptionCapture.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(exceptionHandler)
                .build();
    }

    @Test
    void reportFileDownloadShouldRequireAuthAppAccessAndOwnership() throws Exception {
        User owner = enabledUser(1L);
        User stranger = enabledUser(2L);
        when(userMapper.selectById(1L)).thenReturn(owner);
        when(userMapper.selectById(2L)).thenReturn(stranger);

        String ownerToken = "Bearer " + tokenService.issueToken(1L);
        String strangerToken = "Bearer " + tokenService.issueToken(2L);

        // 未登录直接访问返回 401
        mockMvc.perform(get("/health-records/report-files/report-1.pdf"))
                .andExpect(status().isUnauthorized());

        // 未获健康应用权限返回 403
        when(healthReportMapper.hasHealthAppAccess(2L)).thenReturn(false);
        mockMvc.perform(get("/health-records/report-files/report-1.pdf").header("Authorization", strangerToken))
                .andExpect(status().isForbidden());

        // 有应用权限但非本人文件返回 404
        when(healthReportMapper.hasHealthAppAccess(2L)).thenReturn(true);
        when(healthReportMapper.existsOwnedReportFile(2L, "report-1.pdf", "/api/health-records/report-files/report-1.pdf"))
                .thenReturn(false);
        when(healthVisitMapper.existsOwnedCaseRecordFile(2L, "report-1.pdf", "/api/health-records/report-files/report-1.pdf"))
                .thenReturn(false);
        mockMvc.perform(get("/health-records/report-files/report-1.pdf").header("Authorization", strangerToken))
                .andExpect(status().isNotFound());

        // 拥有者且具备应用权限可下载并附带安全响应头
        when(healthReportMapper.hasHealthAppAccess(1L)).thenReturn(true);
        when(healthReportMapper.existsOwnedReportFile(1L, "report-1.pdf", "/api/health-records/report-files/report-1.pdf"))
                .thenReturn(true);
        mockMvc.perform(get("/health-records/report-files/report-1.pdf").header("Authorization", ownerToken))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    private User enabledUser(Long id) {
        User user = new User();
        user.setId(id);
        user.setRoleCode("USER");
        user.setEnabled(true);
        user.setStatus("ENABLED");
        return user;
    }
}
