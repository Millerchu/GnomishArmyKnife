package com.gak.permission.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gak.framework.exception.BusinessException;
import com.gak.permission.domain.AppAuditLog;
import com.gak.permission.enums.AppAuditActionType;
import com.gak.permission.enums.AppIconStorageType;
import com.gak.permission.mapper.AppAuditLogMapper;
import com.gak.permission.vo.AppIconUploadVO;
import com.gak.user.domain.user.User;
import com.gak.user.enums.user.UserRoleCode;
import com.gak.user.mapper.user.UserMapper;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
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

    private static final long MAX_FILE_SIZE = 2L * 1024L * 1024L;
    private static final Map<String, String> CONTENT_TYPE_EXTENSION_MAP = Map.of(
            "image/png", ".png",
            "image/jpeg", ".jpg",
            "image/gif", ".gif",
            "image/webp", ".webp",
            "image/svg+xml", ".svg"
    );

    private final UserMapper userMapper;
    private final AppAuditLogMapper appAuditLogMapper;
    private final ObjectMapper objectMapper;
    private final Path localStorageDir;
    private final String publicUrlPrefix;

    public SystemAppIconService(UserMapper userMapper,
                                AppAuditLogMapper appAuditLogMapper,
                                ObjectMapper objectMapper,
                                @Value("${gak.app.icon.local-dir:./data/app-icons}") String localDir,
                                @Value("${gak.app.icon.public-url-prefix:/api/system/apps/icon-files/}") String publicUrlPrefix) {
        this.userMapper = userMapper;
        this.appAuditLogMapper = appAuditLogMapper;
        this.objectMapper = objectMapper;
        this.localStorageDir = Paths.get(localDir).toAbsolutePath().normalize();
        this.publicUrlPrefix = publicUrlPrefix.endsWith("/") ? publicUrlPrefix : publicUrlPrefix + "/";
    }

    public AppIconUploadVO upload(Long currentUserId, MultipartFile file, String ip, String userAgent) {
        requireAdminUser(currentUserId);
        validateFile(file);

        String extension = resolveExtension(file);
        String storedFileName = UUID.randomUUID().toString().replace("-", "") + extension;
        Path target = localStorageDir.resolve(storedFileName).normalize();
        if (!target.startsWith(localStorageDir)) {
            throw new BusinessException("APP_ICON_PATH_INVALID", "图标保存路径非法");
        }

        try {
            Files.createDirectories(localStorageDir);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
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
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "图标读取失败");
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("APP_ICON_FILE_REQUIRED", "上传文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException("APP_ICON_FILE_TOO_LARGE", "图标文件不能超过 2MB");
        }
        String contentType = file.getContentType();
        if (!StringUtils.hasText(contentType) || !CONTENT_TYPE_EXTENSION_MAP.containsKey(contentType.toLowerCase(Locale.ROOT))) {
            throw new BusinessException("APP_ICON_FILE_TYPE_INVALID", "仅允许上传图片类型文件");
        }
    }

    private String resolveExtension(MultipartFile file) {
        String contentType = file.getContentType();
        if (StringUtils.hasText(contentType)) {
            String byContentType = CONTENT_TYPE_EXTENSION_MAP.get(contentType.toLowerCase(Locale.ROOT));
            if (byContentType != null) {
                return byContentType;
            }
        }
        String originalFilename = file.getOriginalFilename();
        if (StringUtils.hasText(originalFilename) && originalFilename.contains(".")) {
            return originalFilename.substring(originalFilename.lastIndexOf('.')).toLowerCase(Locale.ROOT);
        }
        throw new BusinessException("APP_ICON_FILE_TYPE_INVALID", "无法识别图标文件类型");
    }

    private String normalizeFileName(String fileName) {
        String normalized = StringUtils.hasText(fileName) ? fileName.trim() : null;
        if (normalized == null || normalized.contains("..") || normalized.contains("/") || normalized.contains("\\")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "图标文件名非法");
        }
        return normalized;
    }

    private MediaType resolveMediaType(Path path) throws IOException {
        String contentType = Files.probeContentType(path);
        if (StringUtils.hasText(contentType)) {
            return MediaType.parseMediaType(contentType);
        }

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
        if (fileName.endsWith(".svg")) {
            return MediaType.parseMediaType("image/svg+xml");
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    private void requireAdminUser(Long currentUserId) {
        User currentUser = userMapper.selectById(currentUserId);
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在");
        }
        if (!UserRoleCode.ADMIN.name().equalsIgnoreCase(currentUser.getRoleCode())) {
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
        auditLog.setIp(StringUtils.hasText(ip) ? ip.trim() : null);
        auditLog.setUserAgent(StringUtils.hasText(userAgent) ? userAgent.trim() : null);
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
