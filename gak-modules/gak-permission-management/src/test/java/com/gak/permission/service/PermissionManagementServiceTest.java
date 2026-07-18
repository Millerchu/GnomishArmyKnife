package com.gak.permission.service;

import com.gak.attachment.service.AttachmentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gak.framework.dictionary.DataDictionaryUsageSupport;
import com.gak.framework.exception.BusinessException;
import com.gak.framework.response.PagedResult;
import com.gak.permission.domain.PermissionAuditLog;
import com.gak.permission.domain.SystemApp;
import com.gak.permission.domain.UserAppPermission;
import com.gak.permission.dto.PermissionUserQueryRequest;
import com.gak.permission.dto.UpdateUserAppPermissionRequest;
import com.gak.permission.mapper.PermissionAuditLogMapper;
import com.gak.permission.mapper.SystemAppMapper;
import com.gak.permission.mapper.UserAppPermissionMapper;
import com.gak.permission.vo.AppCatalogListVO;
import com.gak.permission.vo.PermissionUserListItemVO;
import com.gak.permission.vo.UpdateUserAppPermissionVO;
import com.gak.permission.vo.UserAppPermissionVO;
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
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionManagementServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private SystemAppMapper systemAppMapper;

    @Mock
    private UserAppPermissionMapper userAppPermissionMapper;

    @Mock
    private PermissionAuditLogMapper permissionAuditLogMapper;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private DataDictionaryUsageSupport dataDictionaryUsageSupport;

    @Mock
    private AttachmentService attachmentService;

    @InjectMocks
    private PermissionManagementService permissionManagementService;

    @BeforeEach
    void setUp() {
        lenient().when(attachmentService.listBusinessAttachments(any(), any(), any())).thenReturn(List.of());
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
    void pageUsersShouldReturnPermissionCounts() {
        when(userMapper.selectById(1L)).thenReturn(buildUser(1L, "admin", "ADMIN"));
        when(userMapper.selectList(any())).thenReturn(List.of(
                buildUser(2L, "alice", "USER"),
                buildUser(3L, "bob", "DEV")
        ));
        when(userAppPermissionMapper.selectList(any())).thenReturn(List.of(
                buildPermission(101L, 2L, 2001L, "APP_CALCULATOR"),
                buildPermission(102L, 3L, 2011L, "APP_DISABLED")
        ));
        when(systemAppMapper.selectList(any())).thenReturn(List.of(
                buildApp(2001L, "APP_CALCULATOR", "计算器", true, 10)
        ));

        PermissionUserQueryRequest request = new PermissionUserQueryRequest();
        request.setPageNo(1L);
        request.setPageSize(8L);

        PagedResult<PermissionUserListItemVO> result = permissionManagementService.pageUsers(1L, request);

        assertEquals(2, result.list().size());
        assertEquals(2, result.total());
        assertEquals(1, result.list().get(0).getPermissionCount());
        assertEquals(0, result.list().get(1).getPermissionCount());
    }

    @Test
    void replaceUserAppPermissionsShouldReplaceAndAudit() {
        when(userMapper.selectById(1L)).thenReturn(buildUser(1L, "admin", "ADMIN"));
        when(userMapper.selectById(2L)).thenReturn(buildUser(2L, "alice", "USER"));
        when(userAppPermissionMapper.selectList(any())).thenReturn(
                List.of(buildPermission(201L, 2L, 2001L, "APP_CALCULATOR")),
                List.of(
                        buildPermission(202L, 2L, 2001L, "APP_CALCULATOR"),
                        buildPermission(203L, 2L, 2004L, "APP_TODO_LIST")
                )
        );
        when(systemAppMapper.selectList(any())).thenReturn(
                List.of(buildApp(2001L, "APP_CALCULATOR", "计算器", true, 10)),
                List.of(
                        buildApp(2001L, "APP_CALCULATOR", "计算器", true, 10),
                        buildApp(2004L, "APP_TODO_LIST", "待办列表", true, 40)
                ),
                List.of(
                        buildApp(2001L, "APP_CALCULATOR", "计算器", true, 10),
                        buildApp(2004L, "APP_TODO_LIST", "待办列表", true, 40)
                )
        );
        doAnswer(invocation -> 1).when(userAppPermissionMapper).insert(any(UserAppPermission.class));

        UpdateUserAppPermissionRequest request = new UpdateUserAppPermissionRequest();
        request.setGrantedFeatureCodes(List.of("APP_CALCULATOR", "APP_TODO_LIST"));
        request.setRemark("由权限管理页提交");

        UpdateUserAppPermissionVO result = permissionManagementService.replaceUserAppPermissions(
                1L,
                2L,
                request,
                "trace-1",
                "127.0.0.1",
                "JUnit"
        );

        verify(userAppPermissionMapper).delete(any());
        verify(userAppPermissionMapper, times(2)).insert(any(UserAppPermission.class));
        verify(permissionAuditLogMapper).insert(any(PermissionAuditLog.class));
        assertEquals(2, result.getPermissionCount());
        assertIterableEquals(List.of("APP_CALCULATOR", "APP_TODO_LIST"), result.getGrantedFeatureCodes());
    }

    @Test
    void replaceUserAppPermissionsShouldRejectDisabledApp() {
        when(userMapper.selectById(1L)).thenReturn(buildUser(1L, "admin", "ADMIN"));
        when(userMapper.selectById(2L)).thenReturn(buildUser(2L, "alice", "USER"));
        when(userAppPermissionMapper.selectList(any())).thenReturn(List.of());
        when(systemAppMapper.selectList(any())).thenReturn(List.of(
                buildApp(2001L, "APP_CALCULATOR", "计算器", true, 10)
        ));

        UpdateUserAppPermissionRequest request = new UpdateUserAppPermissionRequest();
        request.setGrantedFeatureCodes(List.of("APP_CALCULATOR", "APP_DISABLED"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> permissionManagementService.replaceUserAppPermissions(
                        1L,
                        2L,
                        request,
                        "trace-2",
                        "127.0.0.1",
                        "JUnit"
                ));
        assertEquals("APP_CODE_INVALID", exception.getCode());
    }

    @Test
    void getCurrentUserAppsShouldReturnEnabledGrantedAppsAndWriteAudit() {
        when(userMapper.selectById(2L)).thenReturn(buildUser(2L, "alice", "USER"));
        when(userAppPermissionMapper.selectList(any())).thenReturn(List.of(
                buildPermission(301L, 2L, 2001L, "APP_CALCULATOR"),
                buildPermission(302L, 2L, 2011L, "APP_DISABLED")
        ));
        when(systemAppMapper.selectList(any())).thenReturn(List.of(
                buildApp(2001L, "APP_CALCULATOR", "计算器", true, 10)
        ));

        UserAppPermissionVO result = permissionManagementService.getCurrentUserApps(
                2L,
                "trace-3",
                "127.0.0.1",
                "JUnit"
        );

        ArgumentCaptor<PermissionAuditLog> captor = ArgumentCaptor.forClass(PermissionAuditLog.class);
        verify(permissionAuditLogMapper).insert(captor.capture());
        assertEquals(2L, result.getUserId());
        assertIterableEquals(List.of("APP_CALCULATOR"), result.getGrantedFeatureCodes());
        assertEquals("DIRECT", result.getPermissionSource());
        assertEquals("QUERY_CURRENT_USER_APPS", captor.getValue().getActionType());
    }

    @Test
    void getCurrentUserAppsShouldFallbackToAllEnabledAppsForAdminWhenPermissionTableIsEmpty() {
        when(userMapper.selectById(1L)).thenReturn(buildUser(1L, "admin", "ADMIN"));
        when(userAppPermissionMapper.selectList(any())).thenReturn(List.of());
        when(systemAppMapper.selectList(any())).thenReturn(List.of(
                buildApp(2001L, "APP_CALCULATOR", "计算器", true, 10),
                buildApp(2004L, "APP_TODO_LIST", "待办列表", true, 40)
        ));

        UserAppPermissionVO result = permissionManagementService.getCurrentUserApps(
                1L,
                "trace-4",
                "127.0.0.1",
                "JUnit"
        );

        assertEquals(1L, result.getUserId());
        assertIterableEquals(List.of("APP_CALCULATOR", "APP_TODO_LIST"), result.getGrantedFeatureCodes());
        assertEquals(2, result.getApps().size());
        assertEquals("ADMIN_FALLBACK", result.getPermissionSource());
    }

    @Test
    void listAppsShouldExposeSystemCatalogSource() {
        when(userMapper.selectById(1L)).thenReturn(buildUser(1L, "admin", "ADMIN"));
        when(systemAppMapper.selectList(any())).thenReturn(List.of(
                buildApp(2001L, "APP_CALCULATOR", "计算器", true, 10)
        ));

        AppCatalogListVO result = permissionManagementService.listApps(1L);

        assertEquals("SYSTEM_APP", result.getCatalogSource());
        assertEquals(1, result.getList().size());
    }

    @Test
    void pageUsersShouldRejectNonAdmin() {
        when(userMapper.selectById(2L)).thenReturn(buildUser(2L, "alice", "USER"));

        PermissionUserQueryRequest request = new PermissionUserQueryRequest();
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> permissionManagementService.pageUsers(2L, request));
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    private User buildUser(Long id, String username, String roleCode) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setDisplayName(username);
        user.setRoleCode(roleCode);
        user.setStatus("ENABLED");
        user.setEnabled(true);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        return user;
    }

    private SystemApp buildApp(Long id, String appCode, String appName, boolean enabled, int sortNo) {
        SystemApp app = new SystemApp();
        app.setId(id);
        app.setAppCode(appCode);
        app.setAppName(appName);
        app.setRoutePath("/" + appCode.toLowerCase());
        app.setCategory("分类");
        app.setDataSourceMode("REAL");
        app.setIconType("TEXT");
        app.setIconText("图标");
        app.setSecurityLevel("PUBLIC");
        app.setEncryptionMode("NONE");
        app.setEnabled(enabled);
        app.setSortNo(sortNo);
        app.setDescription(appName);
        app.setCreatedAt(LocalDateTime.now());
        app.setUpdatedAt(LocalDateTime.now());
        return app;
    }

    private UserAppPermission buildPermission(Long id, Long userId, Long appId, String appCode) {
        UserAppPermission permission = new UserAppPermission();
        permission.setId(id);
        permission.setUserId(userId);
        permission.setAppId(appId);
        permission.setAppCode(appCode);
        permission.setGranted(true);
        permission.setGrantedBy(1L);
        permission.setGrantedAt(LocalDateTime.now());
        permission.setCreatedAt(LocalDateTime.now());
        permission.setUpdatedAt(LocalDateTime.now());
        return permission;
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
