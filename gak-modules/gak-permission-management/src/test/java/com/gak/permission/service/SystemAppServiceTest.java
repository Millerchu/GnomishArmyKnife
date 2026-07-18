package com.gak.permission.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gak.framework.dictionary.DataDictionaryUsageSupport;
import com.gak.attachment.service.AttachmentService;
import com.gak.framework.exception.BusinessException;
import com.gak.framework.response.PagedResult;
import com.gak.permission.domain.AppAuditLog;
import com.gak.permission.domain.SystemApp;
import com.gak.permission.domain.UserAppPermission;
import com.gak.permission.dto.SaveSystemAppRequest;
import com.gak.permission.dto.SystemAppQueryRequest;
import com.gak.permission.dto.UpdateSystemAppStatusRequest;
import com.gak.permission.mapper.AppAuditLogMapper;
import com.gak.permission.mapper.SystemAppMapper;
import com.gak.permission.mapper.UserAppPermissionMapper;
import com.gak.permission.vo.AppCatalogVO;
import com.gak.user.domain.user.User;
import com.gak.user.mapper.user.UserMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemAppServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private SystemAppMapper systemAppMapper;

    @Mock
    private UserAppPermissionMapper userAppPermissionMapper;

    @Mock
    private AppAuditLogMapper appAuditLogMapper;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private DataDictionaryUsageSupport dataDictionaryUsageSupport;

    @Mock
    private AttachmentService attachmentService;

    @InjectMocks
    private SystemAppService systemAppService;

    @BeforeEach
    void setUp() {
        lenient().when(attachmentService.listBusinessAttachments(anyString(), any(), anyString())).thenReturn(List.of());
        lenient().when(dataDictionaryUsageSupport.normalizeValueByUsage(
                eq("APP_APP_MANAGEMENT"),
                eq("SYSTEM_APP"),
                anyString(),
                any(),
                anyBoolean()
        )).thenAnswer(invocation -> normalizeAppField(
                invocation.getArgument(2),
                (String) invocation.getArgument(3),
                invocation.getArgument(4)
        ));
    }

    @Test
    void pageShouldReturnGrantCount() {
        when(userMapper.selectById(1L)).thenReturn(buildUser(1L, "ADMIN"));
        when(systemAppMapper.selectList(any())).thenReturn(List.of(
                buildApp(2001L, "APP_CALCULATOR", true, 10),
                buildApp(2004L, "APP_TODO_LIST", false, 40)
        ));
        when(userAppPermissionMapper.selectList(any())).thenReturn(List.of(
                buildPermission(1L, 2001L, "APP_CALCULATOR"),
                buildPermission(2L, 2001L, "APP_CALCULATOR"),
                buildPermission(3L, 2004L, "APP_TODO_LIST")
        ));

        SystemAppQueryRequest request = new SystemAppQueryRequest();
        request.setPageNo(1L);
        request.setPageSize(8L);

        PagedResult<AppCatalogVO> result = systemAppService.page(1L, request);

        assertEquals(2, result.list().size());
        assertEquals(2, result.list().get(0).getGrantCount());
        assertEquals("DISABLED", result.list().get(1).getStatus());
    }

    @Test
    void createShouldPersistAndAudit() {
        when(userMapper.selectById(1L)).thenReturn(buildUser(1L, "ADMIN"));
        when(systemAppMapper.selectCount(any())).thenReturn(0L);
        doAnswer(invocation -> {
            SystemApp app = invocation.getArgument(0);
            app.setId(3001L);
            return 1;
        }).when(systemAppMapper).insert(any(SystemApp.class));

        SaveSystemAppRequest request = buildSaveRequest();

        AppCatalogVO result = systemAppService.create(1L, request, "127.0.0.1", "JUnit");

        verify(systemAppMapper).insert(any(SystemApp.class));
        verify(appAuditLogMapper).insert(any(AppAuditLog.class));
        assertEquals("APP_EXAMPLE", result.getAppCode());
        assertEquals("ENABLED", result.getStatus());
        assertEquals("grid", result.getIconPreset());
        assertEquals("REAL", result.getDataSourceMode());
    }

    @Test
    void updateShouldRejectAppCodeChange() {
        when(userMapper.selectById(1L)).thenReturn(buildUser(1L, "ADMIN"));
        when(systemAppMapper.selectById(2004L)).thenReturn(buildApp(2004L, "APP_TODO_LIST", true, 40));
        when(userAppPermissionMapper.selectCount(any())).thenReturn(2L);

        SaveSystemAppRequest request = buildSaveRequest();
        request.setAppCode("APP_OTHER");
        request.setFeatureCode("APP_OTHER");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> systemAppService.update(1L, 2004L, request, "127.0.0.1", "JUnit"));
        assertEquals("APP_CODE_IMMUTABLE", exception.getCode());
    }

    @Test
    void updateStatusShouldDisableAppAndAudit() {
        when(userMapper.selectById(1L)).thenReturn(buildUser(1L, "ADMIN"));
        when(systemAppMapper.selectById(2004L)).thenReturn(buildApp(2004L, "APP_TODO_LIST", true, 40));
        when(userAppPermissionMapper.selectCount(any())).thenReturn(3L);

        UpdateSystemAppStatusRequest request = new UpdateSystemAppStatusRequest();
        request.setStatus("DISABLED");
        request.setEnabled(false);

        AppCatalogVO result = systemAppService.updateStatus(1L, 2004L, request, "127.0.0.1", "JUnit");

        ArgumentCaptor<SystemApp> captor = ArgumentCaptor.forClass(SystemApp.class);
        verify(systemAppMapper).updateById(captor.capture());
        verify(appAuditLogMapper).insert(any(AppAuditLog.class));
        assertFalse(captor.getValue().getEnabled());
        assertEquals("DISABLED", result.getStatus());
    }

    @Test
    void createShouldRejectNonAdmin() {
        when(userMapper.selectById(2L)).thenReturn(buildUser(2L, "USER"));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> systemAppService.create(2L, buildSaveRequest(), "127.0.0.1", "JUnit"));
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    @Test
    void createShouldRejectInvalidDataSourceMode() {
        when(userMapper.selectById(1L)).thenReturn(buildUser(1L, "ADMIN"));

        SaveSystemAppRequest request = buildSaveRequest();
        request.setDataSourceMode("MOCK");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> systemAppService.create(1L, request, "127.0.0.1", "JUnit"));
        assertEquals("APP_DATA_SOURCE_MODE_INVALID", exception.getCode());
    }

    private User buildUser(Long id, String roleCode) {
        User user = new User();
        user.setId(id);
        user.setRoleCode(roleCode);
        user.setStatus("ENABLED");
        user.setEnabled(true);
        return user;
    }

    private SystemApp buildApp(Long id, String appCode, boolean enabled, int sortNo) {
        SystemApp app = new SystemApp();
        app.setId(id);
        app.setAppCode(appCode);
        app.setAppName(appCode);
        app.setRoutePath("/demo");
        app.setCategory("分类");
        app.setDataSourceMode("REAL");
        app.setSecurityLevel("PUBLIC");
        app.setEncryptionMode("NONE");
        app.setIconType("TEXT");
        app.setIconText("图标");
        app.setEnabled(enabled);
        app.setSortNo(sortNo);
        app.setDescription("描述");
        app.setRemark("备注");
        app.setCreatedAt(LocalDateTime.now());
        app.setUpdatedAt(LocalDateTime.now());
        return app;
    }

    private UserAppPermission buildPermission(Long userId, Long appId, String appCode) {
        UserAppPermission permission = new UserAppPermission();
        permission.setUserId(userId);
        permission.setAppId(appId);
        permission.setAppCode(appCode);
        permission.setGranted(true);
        permission.setCreatedAt(LocalDateTime.now());
        permission.setUpdatedAt(LocalDateTime.now());
        return permission;
    }

    private SaveSystemAppRequest buildSaveRequest() {
        SaveSystemAppRequest request = new SaveSystemAppRequest();
        request.setAppCode("APP_EXAMPLE");
        request.setFeatureCode("APP_EXAMPLE");
        request.setName("示例应用");
        request.setRoute("/example");
        request.setCategory("演示分组");
        request.setDataSourceMode("REAL");
        request.setSecurityLevel("INTERNAL");
        request.setEncryptionMode("NONE");
        request.setIconType("PRESET");
        request.setIconPreset("grid");
        request.setEnabled(true);
        request.setSortNo(110);
        request.setDescription("示例应用说明");
        request.setRemark("由应用管理页创建");
        return request;
    }

    private String normalizeAppField(String field, String value, boolean required) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isEmpty()) {
            if (required) {
                throw new BusinessException("DICT_ITEM_VALUE_REQUIRED", "字典值不能为空");
            }
            return null;
        }
        return switch (field) {
            case "securityLevel" -> normalizeEnumLike(normalized, List.of("PUBLIC", "INTERNAL", "CONFIDENTIAL"));
            case "dataSourceMode" -> normalizeEnumLike(normalized, List.of("REAL", "DEMO"));
            case "encryptionMode" -> normalizeEnumLike(normalized, List.of("NONE", "FIELD", "END_TO_END"));
            case "iconType" -> normalizeEnumLike(normalized, List.of("PRESET", "UPLOAD", "URL", "TEXT"));
            case "status" -> normalizeEnumLike(normalized, List.of("ENABLED", "DISABLED"));
            default -> normalized;
        };
    }

    private String normalizeEnumLike(String value, List<String> supportedValues) {
        for (String supportedValue : supportedValues) {
            if (supportedValue.equalsIgnoreCase(value)) {
                return supportedValue;
            }
        }
        throw new BusinessException("DICT_ITEM_VALUE_INVALID", "字典值非法");
    }
}
