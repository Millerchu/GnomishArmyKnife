package com.gak.requirementboard.controller;

import com.gak.framework.response.ApiResponse;
import com.gak.requirementboard.dto.CreateRequirementRequest;
import com.gak.requirementboard.dto.RequirementQueryRequest;
import com.gak.requirementboard.dto.UpdateRequirementProgressRequest;
import com.gak.requirementboard.dto.UpdateRequirementRequest;
import com.gak.requirementboard.service.RequirementBoardService;
import com.gak.requirementboard.vo.RequirementDetailVO;
import com.gak.requirementboard.vo.RequirementAppOptionVO;
import com.gak.requirementboard.vo.RequirementPageVO;
import com.gak.user.service.user.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 共享需求看板控制器。
 */
@RestController
@RequestMapping("/requirement-items")
public class RequirementBoardController {

    private final RequirementBoardService requirementBoardService;
    private final TokenService tokenService;

    public RequirementBoardController(RequirementBoardService requirementBoardService, TokenService tokenService) {
        this.requirementBoardService = requirementBoardService;
        this.tokenService = tokenService;
    }

    @GetMapping
    public ApiResponse<RequirementPageVO> page(@Valid RequirementQueryRequest request,
                                                HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(requirementBoardService.page(currentUserId, request));
    }

    @GetMapping("/apps")
    public ApiResponse<List<RequirementAppOptionVO>> listApps(HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(requirementBoardService.listApps(currentUserId));
    }

    @GetMapping("/{id}")
    public ApiResponse<RequirementDetailVO> detail(@PathVariable Long id, HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(requirementBoardService.getDetail(currentUserId, id));
    }

    @PostMapping
    public ApiResponse<RequirementDetailVO> create(@Valid @RequestBody CreateRequirementRequest request,
                                                    HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(requirementBoardService.create(currentUserId, request));
    }

    @PutMapping("/{id}")
    public ApiResponse<RequirementDetailVO> updateContent(@PathVariable Long id,
                                                           @Valid @RequestBody UpdateRequirementRequest request,
                                                           HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(requirementBoardService.updateContent(currentUserId, id, request));
    }

    @PatchMapping("/{id}/progress")
    public ApiResponse<RequirementDetailVO> updateProgress(@PathVariable Long id,
                                                            @Valid @RequestBody UpdateRequirementProgressRequest request,
                                                            HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(requirementBoardService.updateProgress(currentUserId, id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id,
                                    @RequestParam Long version,
                                    HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        requirementBoardService.delete(currentUserId, id, version);
        return ApiResponse.success();
    }
}
