package com.gak.workbench.controller;

import com.gak.framework.response.ApiResponse;
import com.gak.user.service.user.TokenService;
import com.gak.workbench.dto.WorkbenchPreferenceRequest;
import com.gak.workbench.service.WorkbenchPreferenceService;
import com.gak.workbench.vo.WorkbenchPreferenceVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 仅需登录的工作台偏好接口，不提供指定用户或指定 pref_key 的入口。 */
@RestController
@RequestMapping("/workbench/preferences")
public class WorkbenchPreferenceController {
    private final TokenService tokenService;
    private final WorkbenchPreferenceService service;

    public WorkbenchPreferenceController(TokenService tokenService, WorkbenchPreferenceService service) {
        this.tokenService = tokenService;
        this.service = service;
    }

    @GetMapping
    public ApiResponse<WorkbenchPreferenceVO> get(HttpServletRequest request) {
        return ApiResponse.success(service.get(tokenService.requireCurrentUserId(request)));
    }

    @PutMapping
    public ApiResponse<WorkbenchPreferenceVO> save(@Valid @RequestBody WorkbenchPreferenceRequest preference,
            HttpServletRequest request) {
        return ApiResponse.success(service.save(tokenService.requireCurrentUserId(request), preference));
    }
}
