package com.gak.systemlog.controller;

import com.gak.framework.response.ApiResponse;
import com.gak.framework.response.PagedResult;
import com.gak.systemlog.dto.SystemExceptionLogQuery;
import com.gak.systemlog.service.SystemExceptionLogService;
import com.gak.systemlog.vo.SystemExceptionLogDetailVO;
import com.gak.systemlog.vo.SystemExceptionLogItemVO;
import com.gak.user.service.user.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统异常日志查询入口。登录态由 TokenService 校验，应用权限在服务中校验。
 */
@RestController
@RequestMapping("/system/logs")
public class SystemExceptionLogController {

    private final SystemExceptionLogService logService;
    private final TokenService tokenService;

    public SystemExceptionLogController(SystemExceptionLogService logService, TokenService tokenService) {
        this.logService = logService;
        this.tokenService = tokenService;
    }

    @GetMapping
    public ApiResponse<PagedResult<SystemExceptionLogItemVO>> page(HttpServletRequest request,
                                                                    @Valid SystemExceptionLogQuery query) {
        return ApiResponse.success(logService.page(tokenService.requireCurrentUserId(request), query));
    }

    @GetMapping("/{id}")
    public ApiResponse<SystemExceptionLogDetailVO> detail(HttpServletRequest request, @PathVariable Long id) {
        return ApiResponse.success(logService.detail(tokenService.requireCurrentUserId(request), id));
    }
}
