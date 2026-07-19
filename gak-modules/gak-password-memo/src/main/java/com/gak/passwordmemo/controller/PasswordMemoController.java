package com.gak.passwordmemo.controller;

import com.gak.framework.response.ApiResponse;
import com.gak.framework.response.PagedResult;
import com.gak.passwordmemo.dto.PasswordMemoQueryRequest;
import com.gak.passwordmemo.dto.CreatePasswordHistoryRequest;
import com.gak.passwordmemo.dto.SavePasswordMemoRequest;
import com.gak.passwordmemo.dto.UpdateMemoPasswordRequest;
import com.gak.passwordmemo.dto.UpdatePasswordHistoryRequest;
import com.gak.passwordmemo.dto.UpdatePasswordMemoInfoRequest;
import com.gak.passwordmemo.dto.VerifyAccessRequest;
import com.gak.passwordmemo.service.PasswordMemoService;
import com.gak.passwordmemo.vo.PasswordMemoDetailVO;
import com.gak.passwordmemo.vo.PasswordMemoListItemVO;
import com.gak.passwordmemo.vo.VerifyAccessResponse;
import com.gak.user.service.user.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 密码备忘录控制器。
 */
@RestController
@RequestMapping("/password-memos")
public class PasswordMemoController {

    private final PasswordMemoService passwordMemoService;
    private final TokenService tokenService;

    public PasswordMemoController(PasswordMemoService passwordMemoService, TokenService tokenService) {
        this.passwordMemoService = passwordMemoService;
        this.tokenService = tokenService;
    }

    @GetMapping
    public ApiResponse<PagedResult<PasswordMemoListItemVO>> page(@Valid PasswordMemoQueryRequest request,
                                                                 HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(passwordMemoService.page(currentUserId, request));
    }

    @PostMapping
    public ApiResponse<PasswordMemoDetailVO> create(@Valid @RequestBody SavePasswordMemoRequest request,
                                                    HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(passwordMemoService.create(currentUserId, request));
    }

    @PutMapping("/{id}")
    public ApiResponse<PasswordMemoDetailVO> update(@PathVariable Long id,
                                                    @Valid @RequestBody UpdatePasswordMemoInfoRequest request,
                                                    HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(passwordMemoService.update(currentUserId, id, request));
    }

    @PutMapping("/{id}/password")
    public ApiResponse<PasswordMemoDetailVO> updatePassword(@PathVariable Long id,
                                                            @Valid @RequestBody UpdateMemoPasswordRequest request,
                                                            HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(passwordMemoService.updatePassword(currentUserId, id, request));
    }

    @PostMapping("/{id}/password-history")
    public ApiResponse<PasswordMemoDetailVO> createPasswordHistory(
            @PathVariable Long id,
            @Valid @RequestBody CreatePasswordHistoryRequest request,
            HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(passwordMemoService.createPasswordHistory(currentUserId, id, request));
    }

    @PutMapping("/{id}/password-history/{historyId}")
    public ApiResponse<PasswordMemoDetailVO> updatePasswordHistory(
            @PathVariable Long id,
            @PathVariable Long historyId,
            @Valid @RequestBody UpdatePasswordHistoryRequest request,
            HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(passwordMemoService.updatePasswordHistory(currentUserId, id, historyId, request));
    }

    @DeleteMapping("/{id}/password-history/{historyId}")
    public ApiResponse<PasswordMemoDetailVO> deletePasswordHistory(
            @PathVariable Long id,
            @PathVariable Long historyId,
            HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(passwordMemoService.deletePasswordHistory(currentUserId, id, historyId));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        passwordMemoService.delete(currentUserId, id);
        return ApiResponse.success();
    }

    @GetMapping("/{id}")
    public ApiResponse<PasswordMemoDetailVO> get(@PathVariable Long id, HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(passwordMemoService.get(currentUserId, id));
    }

    @PostMapping("/{id}/verify-access")
    public ApiResponse<VerifyAccessResponse> verifyAccess(@PathVariable Long id,
                                                          @Valid @RequestBody VerifyAccessRequest request,
                                                          HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(passwordMemoService.verifyAccess(
                currentUserId,
                id,
                request,
                resolveClientIp(httpServletRequest)
        ));
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
