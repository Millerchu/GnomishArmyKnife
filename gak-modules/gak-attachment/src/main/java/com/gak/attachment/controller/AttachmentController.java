package com.gak.attachment.controller;

import com.gak.attachment.constant.AttachmentConstants;
import com.gak.attachment.dto.SaveUserAvatarRequest;
import com.gak.attachment.service.AttachmentService;
import com.gak.attachment.service.AttachmentService.ResourceFile;
import com.gak.attachment.vo.AttachmentVO;
import com.gak.framework.response.ApiResponse;
import com.gak.user.service.user.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 统一附件上传、预览和删除接口。
 */
@RestController
@RequestMapping("/attachments")
public class AttachmentController {

    private final AttachmentService attachmentService;
    private final TokenService tokenService;

    public AttachmentController(AttachmentService attachmentService, TokenService tokenService) {
        this.attachmentService = attachmentService;
        this.tokenService = tokenService;
    }

    @PostMapping("/upload")
    public ApiResponse<AttachmentVO> upload(@RequestParam("file") MultipartFile file,
                                           @RequestParam("usageType") String usageType,
                                           HttpServletRequest request) {
        return ApiResponse.success(attachmentService.upload(tokenService.requireCurrentUserId(request), file, usageType));
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<Resource> content(@PathVariable Long id, HttpServletRequest request) {
        return buildResourceResponse(attachmentService.loadContent(tokenService.requireCurrentUserId(request), id, false));
    }

    @GetMapping("/{id}/thumbnail")
    public ResponseEntity<Resource> thumbnail(@PathVariable Long id, HttpServletRequest request) {
        return buildResourceResponse(attachmentService.loadContent(tokenService.requireCurrentUserId(request), id, true));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        attachmentService.delete(tokenService.requireCurrentUserId(request), id);
        return ApiResponse.success();
    }

    @GetMapping("/avatar")
    public ApiResponse<List<AttachmentVO>> avatar(HttpServletRequest request) {
        Long currentUserId = tokenService.requireCurrentUserId(request);
        return ApiResponse.success(attachmentService.listBusinessAttachments(
                AttachmentConstants.BUSINESS_USER_AVATAR,
                currentUserId,
                AttachmentConstants.USAGE_IMAGE));
    }

    @PutMapping("/avatar")
    public ApiResponse<List<AttachmentVO>> saveAvatar(@Valid @RequestBody SaveUserAvatarRequest avatarRequest,
                                                       HttpServletRequest request) {
        Long currentUserId = tokenService.requireCurrentUserId(request);
        return ApiResponse.success(attachmentService.syncBusinessAttachments(
                currentUserId,
                AttachmentConstants.BUSINESS_USER_AVATAR,
                currentUserId,
                AttachmentConstants.USAGE_IMAGE,
                avatarRequest.getAttachmentIds(),
                1));
    }

    private ResponseEntity<Resource> buildResourceResponse(ResourceFile file) {
        ContentDisposition disposition = (file.inline() ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(file.originalFileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .cacheControl(CacheControl.noCache().cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(file.resource());
    }
}
