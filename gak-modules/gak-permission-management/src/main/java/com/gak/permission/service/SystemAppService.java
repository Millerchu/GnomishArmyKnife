package com.gak.permission.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gak.framework.exception.BusinessException;
import com.gak.framework.response.PagedResult;
import com.gak.permission.domain.AppAuditLog;
import com.gak.permission.domain.SystemApp;
import com.gak.permission.domain.UserAppPermission;
import com.gak.permission.dto.SaveSystemAppRequest;
import com.gak.permission.dto.SystemAppQueryRequest;
import com.gak.permission.dto.UpdateSystemAppStatusRequest;
import com.gak.permission.enums.AppAuditActionType;
import com.gak.permission.enums.AppEncryptionMode;
import com.gak.permission.enums.AppIconType;
import com.gak.permission.enums.AppIconStorageType;
import com.gak.permission.enums.AppSecurityLevel;
import com.gak.permission.enums.SystemAppStatus;
import com.gak.permission.mapper.AppAuditLogMapper;
import com.gak.permission.mapper.SystemAppMapper;
import com.gak.permission.mapper.UserAppPermissionMapper;
import com.gak.permission.vo.AppCatalogVO;
import com.gak.user.domain.user.User;
import com.gak.user.enums.user.UserRoleCode;
import com.gak.user.mapper.user.UserMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * 应用管理服务。
 */
@Service
public class SystemAppService {

    private static final Pattern PRESET_PATTERN = Pattern.compile("^[a-z0-9-]{2,32}$");
    private static final Comparator<SystemApp> APP_ORDER = Comparator
            .comparing(SystemApp::getSortNo, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(SystemApp::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    private final UserMapper userMapper;
    private final SystemAppMapper systemAppMapper;
    private final UserAppPermissionMapper userAppPermissionMapper;
    private final AppAuditLogMapper appAuditLogMapper;
    private final ObjectMapper objectMapper;

    public SystemAppService(UserMapper userMapper,
                            SystemAppMapper systemAppMapper,
                            UserAppPermissionMapper userAppPermissionMapper,
                            AppAuditLogMapper appAuditLogMapper,
                            ObjectMapper objectMapper) {
        this.userMapper = userMapper;
        this.systemAppMapper = systemAppMapper;
        this.userAppPermissionMapper = userAppPermissionMapper;
        this.appAuditLogMapper = appAuditLogMapper;
        this.objectMapper = objectMapper;
    }

    public PagedResult<AppCatalogVO> page(Long currentUserId, SystemAppQueryRequest request) {
        requireAdminUser(currentUserId);

        QueryWrapper<SystemApp> wrapper = new QueryWrapper<>();
        String keyword = trimToNull(request.getKeyword());
        if (StringUtils.hasText(keyword)) {
            wrapper.and(query -> query.like("app_name", keyword)
                    .or()
                    .like("app_code", keyword)
                    .or()
                    .like("route_path", keyword)
                    .or()
                    .like("category", keyword));
        }
        SystemAppStatus.StatusEnabledPair statusPair = normalizeOptionalStatus(request.getStatus());
        if (statusPair != null) {
            wrapper.eq("enabled", statusPair.enabled());
        }
        String securityLevel = normalizeOptionalSecurityLevel(request.getSecurityLevel());
        if (securityLevel != null) {
            wrapper.eq("security_level", securityLevel);
        }
        wrapper.orderByAsc("sort_no").orderByAsc("id");

        List<SystemApp> apps = systemAppMapper.selectList(wrapper);
        long total = apps.size();
        long fromIndex = Math.max((request.getPageNo() - 1) * request.getPageSize(), 0L);
        long toIndex = Math.min(fromIndex + request.getPageSize(), total);
        if (fromIndex >= total) {
            return new PagedResult<>(Collections.emptyList(), total);
        }

        List<SystemApp> pageApps = new ArrayList<>(apps.subList((int) fromIndex, (int) toIndex));
        Map<String, Integer> grantCountMap = loadGrantCountMap(pageApps);
        List<AppCatalogVO> list = new ArrayList<>();
        for (SystemApp app : pageApps) {
            list.add(toAppCatalogVO(app, grantCountMap.getOrDefault(app.getAppCode(), 0)));
        }
        return new PagedResult<>(list, total);
    }

    @Transactional
    public AppCatalogVO create(Long currentUserId,
                               SaveSystemAppRequest request,
                               String ip,
                               String userAgent) {
        requireAdminUser(currentUserId);
        NormalizedSystemApp normalized = normalizeRequest(request);
        ensureAppCodeUnique(normalized.appCode(), null);

        LocalDateTime now = LocalDateTime.now();
        SystemApp app = new SystemApp();
        app.setAppCode(normalized.appCode());
        applyNormalized(app, normalized);
        app.setCreatedAt(now);
        app.setUpdatedAt(now);
        systemAppMapper.insert(app);

        AppCatalogVO result = toAppCatalogVO(app, 0);
        saveAuditLog(currentUserId, app.getId(), AppAuditActionType.CREATE_APP, null, result, ip, userAgent);
        return result;
    }

    @Transactional
    public AppCatalogVO update(Long currentUserId,
                               Long id,
                               SaveSystemAppRequest request,
                               String ip,
                               String userAgent) {
        requireAdminUser(currentUserId);
        SystemApp current = getAppOrThrow(id);
        AppCatalogVO before = toAppCatalogVO(current, loadGrantCount(current.getAppCode()));
        NormalizedSystemApp normalized = normalizeRequest(request);
        ensureAppCodeImmutable(current, normalized.appCode());

        applyNormalized(current, normalized);
        current.setUpdatedAt(LocalDateTime.now());
        systemAppMapper.updateById(current);

        AppCatalogVO after = toAppCatalogVO(current, before.getGrantCount() != null ? before.getGrantCount() : 0);
        saveAuditLog(currentUserId, current.getId(), AppAuditActionType.UPDATE_APP, before, after, ip, userAgent);
        return after;
    }

    @Transactional
    public AppCatalogVO updateStatus(Long currentUserId,
                                     Long id,
                                     UpdateSystemAppStatusRequest request,
                                     String ip,
                                     String userAgent) {
        requireAdminUser(currentUserId);
        SystemApp current = getAppOrThrow(id);
        AppCatalogVO before = toAppCatalogVO(current, loadGrantCount(current.getAppCode()));
        SystemAppStatus.StatusEnabledPair pair = SystemAppStatus.normalize(
                request.getStatus(),
                request.getEnabled(),
                Boolean.TRUE.equals(current.getEnabled())
        );

        SystemApp updated = new SystemApp();
        updated.setId(id);
        updated.setEnabled(pair.enabled());
        updated.setUpdatedAt(LocalDateTime.now());
        systemAppMapper.updateById(updated);

        current.setEnabled(pair.enabled());
        current.setUpdatedAt(updated.getUpdatedAt());
        AppCatalogVO after = toAppCatalogVO(current, before.getGrantCount() != null ? before.getGrantCount() : 0);

        // 应用管理和权限管理共用同一张应用目录表；只要这里改 enabled，授权保存和主页展示都会自动感知。
        saveAuditLog(currentUserId,
                current.getId(),
                pair.enabled() ? AppAuditActionType.ENABLE_APP : AppAuditActionType.DISABLE_APP,
                before,
                after,
                ip,
                userAgent);
        return after;
    }

    private Map<String, Integer> loadGrantCountMap(List<SystemApp> apps) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (apps.isEmpty()) {
            return result;
        }

        List<String> appCodes = apps.stream()
                .map(SystemApp::getAppCode)
                .filter(StringUtils::hasText)
                .filter(Objects::nonNull)
                .toList();
        if (appCodes.isEmpty()) {
            return result;
        }

        QueryWrapper<UserAppPermission> wrapper = new QueryWrapper<>();
        wrapper.in("app_code", appCodes).eq("granted", true);
        List<UserAppPermission> permissions = userAppPermissionMapper.selectList(wrapper);
        for (UserAppPermission permission : permissions) {
            result.merge(permission.getAppCode(), 1, Integer::sum);
        }
        return result;
    }

    private int loadGrantCount(String appCode) {
        if (!StringUtils.hasText(appCode)) {
            return 0;
        }
        QueryWrapper<UserAppPermission> wrapper = new QueryWrapper<>();
        wrapper.eq("app_code", appCode).eq("granted", true);
        Long count = userAppPermissionMapper.selectCount(wrapper);
        return count == null ? 0 : count.intValue();
    }

    private AppCatalogVO toAppCatalogVO(SystemApp app, int grantCount) {
        validateCatalogMetadata(app);

        AppCatalogVO vo = new AppCatalogVO();
        vo.setId(app.getId());
        vo.setAppCode(app.getAppCode());
        vo.setFeatureCode(app.getAppCode());
        vo.setCode(app.getAppCode());
        vo.setName(app.getAppName());
        vo.setRoute(app.getRoutePath());
        vo.setStatus(Boolean.TRUE.equals(app.getEnabled()) ? SystemAppStatus.ENABLED.name() : SystemAppStatus.DISABLED.name());
        vo.setCategory(app.getCategory());
        vo.setSecurityLevel(app.getSecurityLevel());
        vo.setEncryptionMode(app.getEncryptionMode());
        vo.setEnabled(Boolean.TRUE.equals(app.getEnabled()));
        vo.setSortNo(app.getSortNo());
        vo.setIconType(app.getIconType());
        vo.setIconPreset(app.getIconPreset());
        vo.setIconText(app.getIconText());
        vo.setIconUrl(app.getIconUrl());
        vo.setIconStorageType(app.getIconStorageType());
        vo.setIconFileName(app.getIconFileName());
        vo.setDescription(app.getDescription());
        vo.setRemark(app.getRemark());
        vo.setGrantCount(grantCount);
        return vo;
    }

    private void applyNormalized(SystemApp app, NormalizedSystemApp normalized) {
        app.setAppName(normalized.name());
        app.setRoutePath(normalized.route());
        app.setCategory(normalized.category());
        app.setIconType(normalized.iconType());
        app.setIconPreset(normalized.iconPreset());
        app.setIconText(normalized.iconText());
        app.setIconUrl(normalized.iconUrl());
        app.setIconStorageType(normalized.iconStorageType());
        app.setIconFileName(normalized.iconFileName());
        app.setSecurityLevel(normalized.securityLevel());
        app.setEncryptionMode(normalized.encryptionMode());
        app.setEnabled(normalized.enabled());
        app.setSortNo(normalized.sortNo());
        app.setDescription(normalized.description());
        app.setRemark(normalized.remark());
    }

    private NormalizedSystemApp normalizeRequest(SaveSystemAppRequest request) {
        String appCode = normalizeRequiredAppCode(request.getAppCode());
        String featureCode = trimToNull(request.getFeatureCode());
        if (StringUtils.hasText(featureCode) && !appCode.equalsIgnoreCase(featureCode.trim())) {
            throw new BusinessException("APP_CODE_MISMATCH", "featureCode 与 appCode 必须一致");
        }

        String name = trimToNull(request.getName());
        String route = normalizeRoute(request.getRoute());
        String iconType = normalizeIconType(request.getIconType());
        String iconPreset = normalizeIconPreset(request.getIconPreset());
        String iconText = trimToNull(request.getIconText());
        String iconUrl = trimToNull(request.getIconUrl());
        String iconStorageType = normalizeOptionalIconStorageType(request.getIconStorageType());
        String iconFileName = trimToNull(request.getIconFileName());
        validateIconPayload(iconType, iconPreset, iconText, iconUrl, iconStorageType, iconFileName);

        return new NormalizedSystemApp(
                appCode,
                name,
                route,
                trimToNull(request.getCategory()),
                normalizeRequiredSecurityLevel(request.getSecurityLevel()),
                normalizeRequiredEncryptionMode(request.getEncryptionMode()),
                iconType,
                iconPreset,
                iconText,
                iconUrl,
                iconStorageType,
                iconFileName,
                request.getEnabled() != null ? request.getEnabled() : true,
                request.getSortNo() != null ? request.getSortNo() : 0,
                trimToNull(request.getDescription()),
                trimToNull(request.getRemark())
        );
    }

    private void ensureAppCodeUnique(String appCode, Long excludeId) {
        QueryWrapper<SystemApp> wrapper = new QueryWrapper<>();
        wrapper.eq("app_code", appCode);
        if (excludeId != null) {
            wrapper.ne("id", excludeId);
        }
        Long count = systemAppMapper.selectCount(wrapper);
        if (count != null && count > 0L) {
            throw new BusinessException("APP_CODE_EXISTS", "appCode 已存在");
        }
    }

    private void ensureAppCodeImmutable(SystemApp current, String appCode) {
        if (!current.getAppCode().equalsIgnoreCase(appCode)) {
            throw new BusinessException("APP_CODE_IMMUTABLE", "appCode 创建后不可修改");
        }
    }

    private SystemApp getAppOrThrow(Long id) {
        SystemApp app = systemAppMapper.selectById(id);
        if (app == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "应用不存在");
        }
        return app;
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

    private String normalizeRequiredAppCode(String appCode) {
        String normalized = trimToNull(appCode);
        if (!StringUtils.hasText(normalized)) {
            throw new BusinessException("APP_CODE_REQUIRED", "appCode 不能为空");
        }
        return normalized.toUpperCase();
    }

    private String normalizeRoute(String route) {
        String normalized = trimToNull(route);
        if (normalized == null) {
            return null;
        }
        if (!normalized.startsWith("/")) {
            throw new BusinessException("APP_ROUTE_INVALID", "route 非空时必须以 / 开头");
        }
        return normalized;
    }

    private String normalizeRequiredSecurityLevel(String securityLevel) {
        try {
            return AppSecurityLevel.normalize(securityLevel);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("APP_SECURITY_LEVEL_INVALID", "securityLevel 非法");
        }
    }

    private String normalizeOptionalSecurityLevel(String securityLevel) {
        String normalized = trimToNull(securityLevel);
        if (normalized == null) {
            return null;
        }
        return normalizeRequiredSecurityLevel(normalized);
    }

    private String normalizeRequiredEncryptionMode(String encryptionMode) {
        try {
            return AppEncryptionMode.normalize(encryptionMode);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("APP_ENCRYPTION_MODE_INVALID", "encryptionMode 非法");
        }
    }

    private String normalizeIconType(String iconType) {
        try {
            return AppIconType.normalize(iconType);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("APP_ICON_TYPE_INVALID", "iconType 非法");
        }
    }

    private String normalizeIconPreset(String iconPreset) {
        String normalized = trimToNull(iconPreset);
        if (normalized == null) {
            return null;
        }
        if (!PRESET_PATTERN.matcher(normalized).matches()) {
            throw new BusinessException("APP_ICON_PRESET_INVALID", "iconPreset 非法");
        }
        return normalized;
    }

    private String normalizeOptionalIconStorageType(String iconStorageType) {
        String normalized = trimToNull(iconStorageType);
        if (normalized == null) {
            return null;
        }
        try {
            return AppIconStorageType.normalize(normalized);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("APP_ICON_STORAGE_TYPE_INVALID", "iconStorageType 非法");
        }
    }

    private SystemAppStatus.StatusEnabledPair normalizeOptionalStatus(String status) {
        String normalized = trimToNull(status);
        if (normalized == null) {
            return null;
        }
        return SystemAppStatus.normalize(normalized, null, true);
    }

    private void validateCatalogMetadata(SystemApp app) {
        normalizeRequiredSecurityLevel(app.getSecurityLevel());
        normalizeRequiredEncryptionMode(app.getEncryptionMode());
        app.setIconType(normalizeIconType(app.getIconType()));
        app.setIconStorageType(normalizeOptionalIconStorageType(app.getIconStorageType()));
        app.setIconPreset(normalizeIconPreset(app.getIconPreset()));
        validateIconPayload(
                app.getIconType(),
                app.getIconPreset(),
                app.getIconText(),
                app.getIconUrl(),
                app.getIconStorageType(),
                app.getIconFileName()
        );
    }

    private void validateIconPayload(String iconType,
                                     String iconPreset,
                                     String iconText,
                                     String iconUrl,
                                     String iconStorageType,
                                     String iconFileName) {
        if (AppIconType.PRESET.name().equals(iconType) && !StringUtils.hasText(iconPreset)) {
            throw new BusinessException("APP_ICON_PRESET_REQUIRED", "iconType 为 PRESET 时 iconPreset 不能为空");
        }
        if (AppIconType.UPLOAD.name().equals(iconType)) {
            if (!StringUtils.hasText(iconUrl)) {
                throw new BusinessException("APP_ICON_URL_REQUIRED", "iconType 为 UPLOAD 时 iconUrl 不能为空");
            }
            if (!StringUtils.hasText(iconStorageType)) {
                throw new BusinessException("APP_ICON_STORAGE_TYPE_REQUIRED", "iconType 为 UPLOAD 时 iconStorageType 不能为空");
            }
            if (!StringUtils.hasText(iconFileName)) {
                throw new BusinessException("APP_ICON_FILE_NAME_REQUIRED", "iconType 为 UPLOAD 时 iconFileName 不能为空");
            }
        }
        if (AppIconType.URL.name().equals(iconType) && !StringUtils.hasText(iconUrl)) {
            throw new BusinessException("APP_ICON_URL_REQUIRED", "iconType 为 URL 时 iconUrl 不能为空");
        }
        if (AppIconType.TEXT.name().equals(iconType) && !StringUtils.hasText(iconText)) {
            throw new BusinessException("APP_ICON_TEXT_REQUIRED", "iconType 为 TEXT 时 iconText 不能为空");
        }
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private void saveAuditLog(Long operatorUserId,
                              Long appId,
                              AppAuditActionType actionType,
                              Object before,
                              Object after,
                              String ip,
                              String userAgent) {
        AppAuditLog auditLog = new AppAuditLog();
        auditLog.setOperatorUserId(operatorUserId);
        auditLog.setAppId(appId);
        auditLog.setActionType(actionType.name());
        auditLog.setBeforeJson(toJsonSafely(before));
        auditLog.setAfterJson(toJsonSafely(after));
        auditLog.setIp(trimToNull(ip));
        auditLog.setUserAgent(trimToNull(userAgent));
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

    private record NormalizedSystemApp(String appCode,
                                       String name,
                                       String route,
                                       String category,
                                       String securityLevel,
                                       String encryptionMode,
                                       String iconType,
                                       String iconPreset,
                                       String iconText,
                                       String iconUrl,
                                       String iconStorageType,
                                       String iconFileName,
                                       boolean enabled,
                                       int sortNo,
                                       String description,
                                       String remark) {
    }
}
