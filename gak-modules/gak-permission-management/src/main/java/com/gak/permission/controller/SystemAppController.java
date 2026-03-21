package com.gak.permission.controller;

import com.gak.framework.response.ApiResponse;
import com.gak.framework.response.PagedResult;
import com.gak.permission.service.SystemAppIconService;
import com.gak.permission.dto.SaveSystemAppRequest;
import com.gak.permission.dto.SystemAppQueryRequest;
import com.gak.permission.dto.UpdateSystemAppStatusRequest;
import com.gak.permission.service.SystemAppService;
import com.gak.permission.vo.AppCatalogVO;
import com.gak.permission.vo.AppIconUploadVO;
import com.gak.user.service.user.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

/**
 * 应用管理控制器。
 */
@RestController
@RequestMapping("/system/apps")
public class SystemAppController {

    private final SystemAppService systemAppService;
    private final SystemAppIconService systemAppIconService;
    private final TokenService tokenService;

    public SystemAppController(SystemAppService systemAppService,
                               SystemAppIconService systemAppIconService,
                               TokenService tokenService) {
        this.systemAppService = systemAppService;
        this.systemAppIconService = systemAppIconService;
        this.tokenService = tokenService;
    }

    @GetMapping
    public ApiResponse<PagedResult<AppCatalogVO>> page(@Valid SystemAppQueryRequest request,
                                                       HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(systemAppService.page(currentUserId, request));
    }

    @PostMapping
    public ApiResponse<AppCatalogVO> create(@Valid @RequestBody SaveSystemAppRequest request,
                                            HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(systemAppService.create(
                currentUserId,
                request,
                resolveClientIp(httpServletRequest),
                resolveUserAgent(httpServletRequest)
        ));
    }

    @PutMapping("/{id}")
    public ApiResponse<AppCatalogVO> update(@PathVariable Long id,
                                            @Valid @RequestBody SaveSystemAppRequest request,
                                            HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(systemAppService.update(
                currentUserId,
                id,
                request,
                resolveClientIp(httpServletRequest),
                resolveUserAgent(httpServletRequest)
        ));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<AppCatalogVO> updateStatus(@PathVariable Long id,
                                                  @Valid @RequestBody UpdateSystemAppStatusRequest request,
                                                  HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(systemAppService.updateStatus(
                currentUserId,
                id,
                request,
                resolveClientIp(httpServletRequest),
                resolveUserAgent(httpServletRequest)
        ));
    }

    @PostMapping("/icon-upload")
    public ApiResponse<AppIconUploadVO> uploadIcon(@RequestParam("file") MultipartFile file,
                                                   HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(systemAppIconService.upload(
                currentUserId,
                file,
                resolveClientIp(httpServletRequest),
                resolveUserAgent(httpServletRequest)
        ));
    }

    @GetMapping("/icon-files/{fileName:.+}")
    public ResponseEntity<Resource> getIconFile(@PathVariable String fileName) {
        SystemAppIconService.IconResource iconResource = systemAppIconService.load(fileName);
        return ResponseEntity.ok()
                .contentType(iconResource.mediaType())
                .body(iconResource.resource());
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
