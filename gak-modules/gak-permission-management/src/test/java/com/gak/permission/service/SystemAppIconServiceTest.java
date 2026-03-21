package com.gak.permission.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gak.framework.exception.BusinessException;
import com.gak.permission.domain.AppAuditLog;
import com.gak.permission.vo.AppIconUploadVO;
import com.gak.user.domain.user.User;
import com.gak.user.mapper.user.UserMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemAppIconServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private com.gak.permission.mapper.AppAuditLogMapper appAuditLogMapper;

    @TempDir
    Path tempDir;

    @Test
    void uploadShouldStoreImageAndReturnPublicUrl() throws Exception {
        when(userMapper.selectById(1L)).thenReturn(buildUser(1L, "ADMIN"));
        SystemAppIconService service = buildService();

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "todo.png",
                "image/png",
                new byte[]{1, 2, 3, 4}
        );

        AppIconUploadVO result = service.upload(1L, file, "127.0.0.1", "JUnit");

        assertEquals("FILE_SERVER", result.getIconStorageType());
        assertTrue(result.getIconUrl().startsWith("/api/system/apps/icon-files/"));
        assertTrue(Files.exists(tempDir.resolve(result.getIconFileName())));
        verify(appAuditLogMapper).insert(any(AppAuditLog.class));
    }

    @Test
    void uploadShouldRejectNonImageFile() {
        when(userMapper.selectById(1L)).thenReturn(buildUser(1L, "ADMIN"));
        SystemAppIconService service = buildService();

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "todo.txt",
                "text/plain",
                "not-image".getBytes()
        );

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.upload(1L, file, "127.0.0.1", "JUnit"));
        assertEquals("APP_ICON_FILE_TYPE_INVALID", exception.getCode());
    }

    @Test
    void loadShouldReturnStoredResource() throws Exception {
        SystemAppIconService service = buildService();
        Path stored = tempDir.resolve("demo.png");
        Files.write(stored, new byte[]{1, 2, 3});

        SystemAppIconService.IconResource iconResource = service.load("demo.png");

        Resource resource = iconResource.resource();
        assertTrue(resource.exists());
        assertEquals("image/png", iconResource.mediaType().toString());
    }

    @Test
    void uploadShouldRejectNonAdmin() {
        when(userMapper.selectById(2L)).thenReturn(buildUser(2L, "USER"));
        SystemAppIconService service = buildService();

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "todo.png",
                "image/png",
                new byte[]{1}
        );

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.upload(2L, file, "127.0.0.1", "JUnit"));
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    private SystemAppIconService buildService() {
        return new SystemAppIconService(
                userMapper,
                appAuditLogMapper,
                new ObjectMapper(),
                tempDir.toString(),
                "/api/system/apps/icon-files/"
        );
    }

    private User buildUser(Long id, String roleCode) {
        User user = new User();
        user.setId(id);
        user.setRoleCode(roleCode);
        return user;
    }
}
