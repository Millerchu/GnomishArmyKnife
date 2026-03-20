package com.gak.permission.controller;

import com.gak.framework.response.ApiResponse;
import com.gak.framework.response.PagedResult;
import com.gak.permission.dto.PermissionUserQueryRequest;
import com.gak.permission.dto.UpdateUserAppPermissionRequest;
import com.gak.permission.service.PermissionManagementService;
import com.gak.permission.vo.AppCatalogListVO;
import com.gak.permission.vo.PermissionUserListItemVO;
import com.gak.permission.vo.UpdateUserAppPermissionVO;
import com.gak.permission.vo.UserAppPermissionVO;
import com.gak.user.service.user.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 权限管理控制器。
 */
@RestController
@RequestMapping("/system/permissions")
public class PermissionManagementController {

    private final PermissionManagementService permissionManagementService;
    private final TokenService tokenService;

    public PermissionManagementController(PermissionManagementService permissionManagementService,
                                          TokenService tokenService) {
        this.permissionManagementService = permissionManagementService;
        this.tokenService = tokenService;
    }

    @GetMapping("/users")
    public ApiResponse<PagedResult<PermissionUserListItemVO>> pageUsers(@Valid PermissionUserQueryRequest request,
                                                                        HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(permissionManagementService.pageUsers(currentUserId, request));
    }

    @GetMapping("/apps")
    public ApiResponse<AppCatalogListVO> listApps(HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(permissionManagementService.listApps(currentUserId));
    }

    @GetMapping("/users/{userId}/app-permissions")
    public ApiResponse<UserAppPermissionVO> getUserAppPermissions(@PathVariable Long userId,
                                                                  HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(permissionManagementService.getUserAppPermissions(currentUserId, userId));
    }

    @PutMapping("/users/{userId}/app-permissions")
    public ApiResponse<UpdateUserAppPermissionVO> replaceUserAppPermissions(@PathVariable Long userId,
                                                                            @Valid @RequestBody UpdateUserAppPermissionRequest request,
                                                                            HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(permissionManagementService.replaceUserAppPermissions(
                currentUserId,
                userId,
                request,
                resolveTraceId(httpServletRequest),
                resolveClientIp(httpServletRequest),
                resolveUserAgent(httpServletRequest)
        ));
    }

    @GetMapping("/current-user/apps")
    public ApiResponse<UserAppPermissionVO> getCurrentUserApps(HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(permissionManagementService.getCurrentUserApps(
                currentUserId,
                resolveTraceId(httpServletRequest),
                resolveClientIp(httpServletRequest),
                resolveUserAgent(httpServletRequest)
        ));
    }

    private String resolveTraceId(HttpServletRequest request) {
        String traceId = request.getHeader("X-Trace-Id");
        return StringUtils.hasText(traceId) ? traceId.trim() : UUID.randomUUID().toString();
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String resolveUserAgent(HttpServletRequest request) {
        return request.getHeader("User-Agent");
    }
}
