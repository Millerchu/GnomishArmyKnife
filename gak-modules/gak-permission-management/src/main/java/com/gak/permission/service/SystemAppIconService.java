package com.gak.permission.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.gak.attachment.constant.AttachmentConstants;
import com.gak.attachment.service.AttachmentFileProcessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gak.framework.exception.BusinessException;
import com.gak.permission.domain.AppAuditLog;
import com.gak.permission.enums.AppAuditActionType;
import com.gak.permission.enums.AppIconStorageType;
import com.gak.permission.mapper.AppAuditLogMapper;
import com.gak.permission.vo.AppIconUploadVO;
import com.gak.user.constant.UserSecurityConstants;
import com.gak.user.domain.user.User;
import com.gak.user.mapper.user.UserMapper;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * 应用图标上传与读取服务。
 */
@Service
public class SystemAppIconService {

    private final AttachmentFileProcessor fileProcessor;
    private final UserMapper userMapper;
    private final AppAuditLogMapper appAuditLogMapper;
    private final ObjectMapper objectMapper;
    private final Path localStorageDir;
    private final String publicUrlPrefix;

    public SystemAppIconService(UserMapper userMapper,
                                AppAuditLogMapper appAuditLogMapper,
                                ObjectMapper objectMapper,
                                AttachmentFileProcessor fileProcessor,
                                @Value("${gak.app.icon.local-dir:./data/app-icons}") String localDir,
                                @Value("${gak.app.icon.public-url-prefix:/api/system/apps/icon-files/}") String publicUrlPrefix) {
        this.userMapper = userMapper;
        this.fileProcessor = fileProcessor;
        this.appAuditLogMapper = appAuditLogMapper;
        this.objectMapper = objectMapper;
        this.localStorageDir = Paths.get(localDir).toAbsolutePath().normalize();
        this.publicUrlPrefix = publicUrlPrefix.endsWith("/") ? publicUrlPrefix : publicUrlPrefix + "/";
    }

    public AppIconUploadVO upload(Long currentUserId, MultipartFile file, String ip, String userAgent) {
        requireAdminUser(currentUserId);
        AttachmentFileProcessor.ProcessedFile icon = fileProcessor.process(file, AttachmentConstants.USAGE_ICON);
        String storedFileName = UUID.randomUUID().toString().replace("-", "") + icon.extension();
        Path target = localStorageDir.resolve(storedFileName).normalize();
        if (!target.startsWith(localStorageDir)) {
            throw new BusinessException("APP_ICON_PATH_INVALID", "图标保存路径非法");
        }

        try {
            Files.createDirectories(localStorageDir);
            Files.write(target, icon.content(), java.nio.file.StandardOpenOption.CREATE_NEW);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "图标上传失败");
        }

        AppIconUploadVO result = new AppIconUploadVO();
        result.setIconUrl(publicUrlPrefix + storedFileName);
        result.setIconStorageType(AppIconStorageType.FILE_SERVER.name());
        result.setIconFileName(storedFileName);

        saveAuditLog(currentUserId, AppAuditActionType.UPLOAD_ICON, result, ip, userAgent);
        return result;
    }

    public IconResource load(String fileName) {
        String normalized = normalizeFileName(fileName);
        Path path = localStorageDir.resolve(normalized).normalize();
        if (!path.startsWith(localStorageDir) || !Files.exists(path) || !Files.isRegularFile(path)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "图标不存在");
        }

        try {
            Resource resource = new UrlResource(path.toUri());
            MediaType mediaType = resolveMediaType(path);
            return new IconResource(resource, mediaType);
        } catch (MalformedURLException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "图标不存在");
        }
    }

    private String normalizeFileName(String fileName) {
        String normalized = StringUtils.hasText(fileName) ? fileName.trim() : null;
        if (normalized == null || normalized.contains("..") || normalized.contains("/") || normalized.contains("\\")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "图标文件名非法");
        }
        return normalized;
    }

    private MediaType resolveMediaType(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (fileName.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG;
        }
        if (fileName.endsWith(".gif")) {
            return MediaType.IMAGE_GIF;
        }
        if (fileName.endsWith(".webp")) {
            return MediaType.parseMediaType("image/webp");
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    private void requireAdminUser(Long currentUserId) {
        User currentUser = userMapper.selectById(currentUserId);
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在");
        }
        if (!UserSecurityConstants.ADMIN_ROLE_CODE.equalsIgnoreCase(currentUser.getRoleCode())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅管理员可操作应用管理");
        }
    }

    private void saveAuditLog(Long operatorUserId,
                              AppAuditActionType actionType,
                              Object after,
                              String ip,
                              String userAgent) {
        AppAuditLog auditLog = new AppAuditLog();
        auditLog.setOperatorUserId(operatorUserId);
        auditLog.setAppId(null);
        auditLog.setActionType(actionType.name());
        auditLog.setAfterJson(toJsonSafely(after));
        auditLog.setIp(PermissionAuditFields.ip(ip));
        auditLog.setUserAgent(PermissionAuditFields.userAgent(userAgent));
        auditLog.setCreatedAt(LocalDateTime.now());
        appAuditLogMapper.insert(auditLog);
    }

    private String toJsonSafely(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return "{\"message\":\"json serialize failed\"}";
        }
    }

    public record IconResource(Resource resource, MediaType mediaType) {
    }
}
