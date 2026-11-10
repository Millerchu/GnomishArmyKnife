package com.gak.permission.controller;

import com.gak.permission.dto.UpdateUserAppPermissionRequest;
import com.gak.permission.service.PermissionManagementService;
import com.gak.user.service.user.TokenService;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证两条权限审计入口的请求追踪编号兼容性与输入边界。
 */
@ExtendWith(MockitoExtension.class)
class PermissionManagementControllerTest {

    @Mock
    private PermissionManagementService permissionManagementService;

    @Mock
    private TokenService tokenService;

    @InjectMocks
    private PermissionManagementController controller;

    @ParameterizedTest
    @MethodSource("validTraceIds")
    void shouldForwardTrimmedValidTraceId(String header, String expectedTraceId) {
        for (String traceId : captureForwardedTraceIds(header)) {
            assertEquals(expectedTraceId, traceId);
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @MethodSource("invalidTraceIds")
    void shouldGenerateUuidForMissingBlankOrInvalidTraceId(String header) {
        for (String traceId : captureForwardedTraceIds(header)) {
            assertEquals(36, traceId.length());
            assertEquals(traceId, UUID.fromString(traceId).toString());
            assertEquals(4, UUID.fromString(traceId).version());
            assertNotEquals(header, traceId);
        }
    }

    private static Stream<Arguments> validTraceIds() {
        String firstVisibleAsciiRange = IntStream.rangeClosed(0x21, 0x5f)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
        String lastVisibleAsciiRange = IntStream.rangeClosed(0x60, 0x7e)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
        return Stream.of(
                Arguments.of(" \tTrace-MiXeD_123:abc/XYZ\r\n", "Trace-MiXeD_123:abc/XYZ"),
                Arguments.of("A".repeat(64), "A".repeat(64)),
                Arguments.of("  " + "B".repeat(64) + "  ", "B".repeat(64)),
                Arguments.of(firstVisibleAsciiRange, firstVisibleAsciiRange),
                Arguments.of(lastVisibleAsciiRange, lastVisibleAsciiRange)
        );
    }

    private static Stream<String> invalidTraceIds() {
        return Stream.of(
                "A".repeat(65),
                "  " + "B".repeat(65) + "  ",
                "trace with-space",
                "trace\tcontrol",
                "trace\r\ncontrol",
                "trace\u0000control",
                "trace\u007fcontrol",
                "trace-中文",
                "trace-é",
                "trace-\u00a0space",
                "trace-\u2003space",
                "trace-😀",
                "   ",
                "\t\r\n"
        );
    }

    /**
     * 直接调用控制器并捕获服务入参，避免启动服务或依赖数据库。
     */
    private List<String> captureForwardedTraceIds(String header) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (header != null) {
            request.addHeader("X-Trace-Id", header);
        }
        when(tokenService.requireCurrentUserId(request)).thenReturn(1L);
        UpdateUserAppPermissionRequest updateRequest = new UpdateUserAppPermissionRequest();

        controller.replaceUserAppPermissions(2L, updateRequest, request);
        controller.getCurrentUserApps(request);

        ArgumentCaptor<String> traceIds = ArgumentCaptor.forClass(String.class);
        verify(permissionManagementService).replaceUserAppPermissions(
                eq(1L), eq(2L), eq(updateRequest), traceIds.capture(), eq(request.getRemoteAddr()), isNull());
        verify(permissionManagementService).getCurrentUserApps(
                eq(1L), traceIds.capture(), eq(request.getRemoteAddr()), isNull());
        return traceIds.getAllValues();
    }
}
