package com.gak.user.controller.user;

import com.gak.framework.response.ApiResponse;
import com.gak.framework.response.PagedResult;
import com.gak.user.dto.user.CreateUserRequest;
import com.gak.user.dto.user.ResetPasswordRequest;
import com.gak.user.dto.user.UpdateUserRequest;
import com.gak.user.dto.user.UpdateUserStatusRequest;
import com.gak.user.dto.user.UserQueryRequest;
import com.gak.user.service.user.SystemUserService;
import com.gak.user.service.user.TokenService;
import com.gak.user.vo.user.UserListItemVO;
import com.gak.user.vo.user.UserProfileVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台用户管理控制器。
 */
@RestController
@RequestMapping("/system/users")
public class SystemUserController {

    private final SystemUserService systemUserService;
    private final TokenService tokenService;

    public SystemUserController(SystemUserService systemUserService, TokenService tokenService) {
        this.systemUserService = systemUserService;
        this.tokenService = tokenService;
    }

    @GetMapping
    public ApiResponse<PagedResult<UserListItemVO>> page(@Valid UserQueryRequest request,
                                                         HttpServletRequest httpServletRequest) {
        tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(systemUserService.page(request));
    }

    @PostMapping
    public ApiResponse<UserProfileVO> create(@Valid @RequestBody CreateUserRequest request,
                                             HttpServletRequest httpServletRequest) {
        tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(systemUserService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<UserProfileVO> update(@PathVariable Long id,
                                             @Valid @RequestBody UpdateUserRequest request,
                                             HttpServletRequest httpServletRequest) {
        tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(systemUserService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        systemUserService.delete(id, currentUserId);
        return ApiResponse.success();
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<Void> updateStatus(@PathVariable Long id,
                                          @Valid @RequestBody UpdateUserStatusRequest request,
                                          HttpServletRequest httpServletRequest) {
        tokenService.requireCurrentUserId(httpServletRequest);
        systemUserService.updateStatus(id, request);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/reset-password")
    public ApiResponse<Void> resetPassword(@PathVariable Long id,
                                           @Valid @RequestBody ResetPasswordRequest request,
                                           HttpServletRequest httpServletRequest) {
        tokenService.requireCurrentUserId(httpServletRequest);
        systemUserService.resetPassword(id, request);
        return ApiResponse.success();
    }
}
