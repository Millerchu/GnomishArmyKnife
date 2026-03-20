package com.gak.permission.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gak.framework.exception.BusinessException;
import com.gak.framework.response.PagedResult;
import com.gak.permission.domain.PermissionAuditLog;
import com.gak.permission.domain.SystemApp;
import com.gak.permission.domain.UserAppPermission;
import com.gak.permission.dto.PermissionUserQueryRequest;
import com.gak.permission.dto.UpdateUserAppPermissionRequest;
import com.gak.permission.enums.AppEncryptionMode;
import com.gak.permission.enums.AppIconType;
import com.gak.permission.enums.AppSecurityLevel;
import com.gak.permission.enums.PermissionAuditActionType;
import com.gak.permission.mapper.PermissionAuditLogMapper;
import com.gak.permission.mapper.SystemAppMapper;
import com.gak.permission.mapper.UserAppPermissionMapper;
import com.gak.permission.vo.AppCatalogListVO;
import com.gak.permission.vo.AppCatalogVO;
import com.gak.permission.vo.PermissionUserListItemVO;
import com.gak.permission.vo.UpdateUserAppPermissionVO;
import com.gak.permission.vo.UserAppPermissionVO;
import com.gak.user.domain.user.User;
import com.gak.user.enums.user.UserRoleCode;
import com.gak.user.enums.user.UserStatus;
import com.gak.user.mapper.user.UserMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * 权限管理服务。
 */
@Service
public class PermissionManagementService {

    private static final Comparator<SystemApp> APP_ORDER = Comparator
            .comparing(SystemApp::getSortNo, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(SystemApp::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    private final UserMapper userMapper;
    private final SystemAppMapper systemAppMapper;
    private final UserAppPermissionMapper userAppPermissionMapper;
    private final PermissionAuditLogMapper permissionAuditLogMapper;
    private final ObjectMapper objectMapper;

    public PermissionManagementService(UserMapper userMapper,
                                       SystemAppMapper systemAppMapper,
                                       UserAppPermissionMapper userAppPermissionMapper,
                                       PermissionAuditLogMapper permissionAuditLogMapper,
                                       ObjectMapper objectMapper) {
        this.userMapper = userMapper;
        this.systemAppMapper = systemAppMapper;
        this.userAppPermissionMapper = userAppPermissionMapper;
        this.permissionAuditLogMapper = permissionAuditLogMapper;
        this.objectMapper = objectMapper;
    }

    public PagedResult<PermissionUserListItemVO> pageUsers(Long currentUserId, PermissionUserQueryRequest request) {
        requireAdminUser(currentUserId);

        QueryWrapper<User> wrapper = new QueryWrapper<>();
        String keyword = trimToNull(request.getKeyword());
        if (StringUtils.hasText(keyword)) {
            wrapper.and(query -> query.like("username", keyword)
                    .or()
                    .like("display_name", keyword)
                    .or()
                    .like("phone", keyword)
                    .or()
                    .like("email", keyword));
        }
        String status = normalizeOptionalStatus(request.getStatus());
        if (status != null) {
            wrapper.eq("status", status);
        }
        String roleCode = normalizeOptionalRoleCode(request.getRoleCode());
        if (roleCode != null) {
            wrapper.eq("role_code", roleCode);
        }
        wrapper.orderByDesc("created_at").orderByDesc("id");

        List<User> users = userMapper.selectList(wrapper);
        long total = users.size();
        long fromIndex = Math.max((request.getPageNo() - 1) * request.getPageSize(), 0L);
        long toIndex = Math.min(fromIndex + request.getPageSize(), total);
        if (fromIndex >= total) {
            return new PagedResult<>(Collections.emptyList(), total);
        }

        List<User> pageUsers = users.subList((int) fromIndex, (int) toIndex);
        Map<Long, Integer> permissionCountMap = loadPermissionCountMap(pageUsers);
        List<PermissionUserListItemVO> list = new ArrayList<>();
        for (User user : pageUsers) {
            list.add(toPermissionUserListItem(user, permissionCountMap.getOrDefault(user.getId(), 0)));
        }
        return new PagedResult<>(list, total);
    }

    public AppCatalogListVO listApps(Long currentUserId) {
        requireAdminUser(currentUserId);

        QueryWrapper<SystemApp> wrapper = new QueryWrapper<>();
        wrapper.orderByAsc("sort_no").orderByAsc("id");
        List<SystemApp> apps = systemAppMapper.selectList(wrapper);

        AppCatalogListVO result = new AppCatalogListVO();
        result.setList(toAppCatalogList(apps));
        return result;
    }

    public UserAppPermissionVO getUserAppPermissions(Long currentUserId, Long userId) {
        requireAdminUser(currentUserId);
        getUserOrThrow(userId);
        return buildUserAppPermissionVO(userId);
    }

    @Transactional
    public UpdateUserAppPermissionVO replaceUserAppPermissions(Long currentUserId,
                                                               Long userId,
                                                               UpdateUserAppPermissionRequest request,
                                                               String traceId,
                                                               String ip,
                                                               String userAgent) {
        requireAdminUser(currentUserId);
        getUserOrThrow(userId);

        UserAppPermissionVO before = buildUserAppPermissionVO(userId);
        List<String> grantedFeatureCodes = normalizeFeatureCodes(request.getGrantedFeatureCodes());
        Map<String, SystemApp> enabledApps = loadEnabledAppMap(grantedFeatureCodes);
        if (enabledApps.size() != grantedFeatureCodes.size()) {
            throw new BusinessException("APP_CODE_INVALID", "grantedFeatureCodes 包含不存在或未启用的应用");
        }

        // 前端保存是整包覆盖语义，这里按用户维度先清空后重建，避免历史授权残留。
        QueryWrapper<UserAppPermission> deleteWrapper = new QueryWrapper<>();
        deleteWrapper.eq("user_id", userId);
        userAppPermissionMapper.delete(deleteWrapper);

        LocalDateTime now = LocalDateTime.now();
        String remark = trimToNull(request.getRemark());
        for (String featureCode : grantedFeatureCodes) {
            SystemApp app = enabledApps.get(featureCode);
            UserAppPermission permission = new UserAppPermission();
            permission.setUserId(userId);
            permission.setAppId(app.getId());
            permission.setAppCode(app.getAppCode());
            permission.setGranted(true);
            permission.setGrantedBy(currentUserId);
            permission.setGrantedAt(now);
            permission.setRemark(remark);
            permission.setCreatedAt(now);
            permission.setUpdatedAt(now);
            userAppPermissionMapper.insert(permission);
        }

        UserAppPermissionVO after = buildUserAppPermissionVO(userId);
        saveAuditLog(currentUserId,
                userId,
                PermissionAuditActionType.REPLACE_APPS,
                before,
                after,
                traceId,
                ip,
                userAgent);

        UpdateUserAppPermissionVO result = new UpdateUserAppPermissionVO();
        result.setUserId(userId);
        result.setGrantedFeatureCodes(after.getGrantedFeatureCodes());
        result.setPermissionCount(after.getGrantedFeatureCodes().size());
        return result;
    }

    public UserAppPermissionVO getCurrentUserApps(Long currentUserId, String traceId, String ip, String userAgent) {
        getUserOrThrow(currentUserId);

        // 主页显示必须以授权表为准，而不是让前端继续写死应用常量。
        UserAppPermissionVO result = buildUserAppPermissionVO(currentUserId);
        saveAuditLog(currentUserId,
                currentUserId,
                PermissionAuditActionType.QUERY_CURRENT_USER_APPS,
                null,
                result,
                traceId,
                ip,
                userAgent);
        return result;
    }

    private Map<Long, Integer> loadPermissionCountMap(List<User> users) {
        Map<Long, Integer> result = new LinkedHashMap<>();
        if (users.isEmpty()) {
            return result;
        }

        List<Long> userIds = users.stream().map(User::getId).filter(Objects::nonNull).toList();
        QueryWrapper<UserAppPermission> permissionWrapper = new QueryWrapper<>();
        permissionWrapper.in("user_id", userIds).eq("granted", true);
        List<UserAppPermission> permissions = userAppPermissionMapper.selectList(permissionWrapper);
        if (permissions.isEmpty()) {
            return result;
        }

        Set<String> grantedCodes = permissions.stream()
                .map(UserAppPermission::getAppCode)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (grantedCodes.isEmpty()) {
            return result;
        }

        Map<String, SystemApp> enabledApps = loadEnabledAppMap(new ArrayList<>(grantedCodes));
        for (UserAppPermission permission : permissions) {
            if (enabledApps.containsKey(permission.getAppCode())) {
                result.merge(permission.getUserId(), 1, Integer::sum);
            }
        }
        return result;
    }

    private UserAppPermissionVO buildUserAppPermissionVO(Long userId) {
        List<SystemApp> apps = loadEnabledGrantedApps(userId);

        UserAppPermissionVO vo = new UserAppPermissionVO();
        vo.setUserId(userId);
        vo.setGrantedFeatureCodes(apps.stream().map(SystemApp::getAppCode).toList());
        vo.setApps(toAppCatalogList(apps));
        return vo;
    }

    private List<SystemApp> loadEnabledGrantedApps(Long userId) {
        QueryWrapper<UserAppPermission> permissionWrapper = new QueryWrapper<>();
        permissionWrapper.eq("user_id", userId).eq("granted", true);
        List<UserAppPermission> permissions = userAppPermissionMapper.selectList(permissionWrapper);
        if (permissions.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> codes = permissions.stream()
                .map(UserAppPermission::getAppCode)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        if (codes.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, SystemApp> enabledAppMap = loadEnabledAppMap(codes);
        List<SystemApp> apps = new ArrayList<>(enabledAppMap.values());
        apps.sort(APP_ORDER);
        return apps;
    }

    private Map<String, SystemApp> loadEnabledAppMap(List<String> appCodes) {
        if (appCodes.isEmpty()) {
            return Collections.emptyMap();
        }

        QueryWrapper<SystemApp> wrapper = new QueryWrapper<>();
        wrapper.in("app_code", appCodes).eq("enabled", true);
        List<SystemApp> apps = systemAppMapper.selectList(wrapper);

        Map<String, SystemApp> result = new LinkedHashMap<>();
        for (SystemApp app : apps) {
            validateCatalogMetadata(app);
            result.put(app.getAppCode(), app);
        }
        return result;
    }

    private List<AppCatalogVO> toAppCatalogList(List<SystemApp> apps) {
        List<SystemApp> sortedApps = new ArrayList<>(apps);
        sortedApps.sort(APP_ORDER);

        List<AppCatalogVO> result = new ArrayList<>();
        for (SystemApp app : sortedApps) {
            result.add(toAppCatalogVO(app));
        }
        return result;
    }

    private AppCatalogVO toAppCatalogVO(SystemApp app) {
        validateCatalogMetadata(app);

        AppCatalogVO vo = new AppCatalogVO();
        vo.setId(app.getId());
        vo.setAppCode(app.getAppCode());
        vo.setFeatureCode(app.getAppCode());
        vo.setCode(app.getAppCode());
        vo.setName(app.getAppName());
        vo.setRoute(app.getRoutePath());
        vo.setCategory(app.getCategory());
        vo.setSecurityLevel(app.getSecurityLevel());
        vo.setEncryptionMode(app.getEncryptionMode());
        vo.setEnabled(Boolean.TRUE.equals(app.getEnabled()));
        vo.setSortNo(app.getSortNo());
        vo.setIconType(app.getIconType());
        vo.setIconText(app.getIconText());
        vo.setIconUrl(app.getIconUrl());
        vo.setDescription(app.getDescription());
        return vo;
    }

    private PermissionUserListItemVO toPermissionUserListItem(User user, int permissionCount) {
        PermissionUserListItemVO vo = new PermissionUserListItemVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setDisplayName(user.getDisplayName());
        vo.setPhone(user.getPhone());
        vo.setEmail(user.getEmail());
        vo.setRoleCode(resolveRoleCode(user));
        vo.setStatus(resolveStatus(user));
        vo.setEnabled(resolveEnabled(user));
        vo.setPermissionCount(permissionCount);
        return vo;
    }

    private void requireAdminUser(Long currentUserId) {
        User currentUser = getUserOrThrow(currentUserId);
        // 权限管理页属于系统管理域，只有管理员才能查看用户授权列表和改别人的权限。
        if (!UserRoleCode.ADMIN.name().equalsIgnoreCase(resolveRoleCode(currentUser))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅管理员可操作权限管理");
        }
    }

    private User getUserOrThrow(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在");
        }
        return user;
    }

    private void validateCatalogMetadata(SystemApp app) {
        try {
            AppSecurityLevel.normalize(app.getSecurityLevel());
            AppEncryptionMode.normalize(app.getEncryptionMode());
            app.setIconType(AppIconType.normalize(app.getIconType()));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("APP_CATALOG_INVALID", "应用目录存在非法配置");
        }
    }

    private String normalizeOptionalStatus(String status) {
        String normalized = trimToNull(status);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toUpperCase();
        if (!UserStatus.isValid(normalized)) {
            throw new BusinessException("USER_STATUS_INVALID", "status 非法");
        }
        return normalized;
    }

    private String normalizeOptionalRoleCode(String roleCode) {
        String normalized = trimToNull(roleCode);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toUpperCase();
        if (!UserRoleCode.isValid(normalized)) {
            throw new BusinessException("ROLE_CODE_INVALID", "roleCode 非法");
        }
        return normalized;
    }

    private List<String> normalizeFeatureCodes(List<String> featureCodes) {
        if (featureCodes == null || featureCodes.isEmpty()) {
            return Collections.emptyList();
        }

        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String featureCode : featureCodes) {
            String normalized = trimToNull(featureCode);
            if (normalized == null) {
                throw new BusinessException("APP_CODE_INVALID", "grantedFeatureCodes 不能为空字符串");
            }
            result.add(normalized.toUpperCase());
        }
        return new ArrayList<>(result);
    }

    private String resolveRoleCode(User user) {
        return StringUtils.hasText(user.getRoleCode()) ? user.getRoleCode() : UserRoleCode.USER.name();
    }

    private String resolveStatus(User user) {
        return StringUtils.hasText(user.getStatus()) ? user.getStatus() : UserStatus.ENABLED.name();
    }

    private boolean resolveEnabled(User user) {
        String status = resolveStatus(user);
        return user.getEnabled() != null ? user.getEnabled() : UserStatus.fromCode(status).isEnabled();
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private void saveAuditLog(Long operatorUserId,
                              Long targetUserId,
                              PermissionAuditActionType actionType,
                              Object before,
                              Object after,
                              String traceId,
                              String ip,
                              String userAgent) {
        PermissionAuditLog auditLog = new PermissionAuditLog();
        auditLog.setOperatorUserId(operatorUserId);
        auditLog.setTargetUserId(targetUserId);
        auditLog.setActionType(actionType.name());
        auditLog.setBeforeJson(toJsonSafely(before));
        auditLog.setAfterJson(toJsonSafely(after));
        auditLog.setTraceId(trimToNull(traceId));
        auditLog.setIp(trimToNull(ip));
        auditLog.setUserAgent(trimToNull(userAgent));
        auditLog.setCreatedAt(LocalDateTime.now());
        permissionAuditLogMapper.insert(auditLog);
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
}
