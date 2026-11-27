package com.gak.permission.controller;

import com.gak.framework.security.PublicEndpoint;
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
                httpServletRequest.getRemoteAddr(),
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
                httpServletRequest.getRemoteAddr(),
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
                httpServletRequest.getRemoteAddr(),
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
                httpServletRequest.getRemoteAddr(),
                resolveUserAgent(httpServletRequest)
        ));
    }

    @PublicEndpoint
    @GetMapping("/icon-files/{fileName:.+}")
    public ResponseEntity<Resource> getIconFile(@PathVariable String fileName) {
        SystemAppIconService.IconResource iconResource = systemAppIconService.load(fileName);
        var response = ResponseEntity.ok().contentType(iconResource.mediaType())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; sandbox");
        if (org.springframework.http.MediaType.APPLICATION_OCTET_STREAM.equals(iconResource.mediaType())) {
            response.header("Content-Disposition", "attachment");
        }
        return response.body(iconResource.resource());
    }

    private String resolveUserAgent(HttpServletRequest request) {
        return request.getHeader("User-Agent");
    }
}
