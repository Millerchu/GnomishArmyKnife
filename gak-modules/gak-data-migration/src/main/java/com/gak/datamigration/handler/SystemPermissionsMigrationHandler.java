package com.gak.datamigration.handler;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gak.datamigration.DataMigrationConstants;
import com.gak.datamigration.service.DataMigrationArchiveService;
import com.gak.datamigration.service.DataMigrationBeanMergeSupport;
import com.gak.framework.exception.BusinessException;
import com.gak.permission.domain.SystemApp;
import com.gak.permission.domain.UserAppPermission;
import com.gak.permission.mapper.SystemAppMapper;
import com.gak.permission.mapper.UserAppPermissionMapper;
import com.gak.user.domain.user.User;
import com.gak.user.mapper.user.UserMapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 用户授权迁移处理器。
 */
@Service
public class SystemPermissionsMigrationHandler implements MigrationResourceHandler {

    private final UserAppPermissionMapper permissionMapper;
    private final UserMapper userMapper;
    private final SystemAppMapper systemAppMapper;
    private final DataMigrationArchiveService archiveService;

    public SystemPermissionsMigrationHandler(UserAppPermissionMapper permissionMapper,
                                             UserMapper userMapper,
                                             SystemAppMapper systemAppMapper,
                                             DataMigrationArchiveService archiveService) {
        this.permissionMapper = permissionMapper;
        this.userMapper = userMapper;
        this.systemAppMapper = systemAppMapper;
        this.archiveService = archiveService;
    }

    @Override
    public String resourceCode() {
        return DataMigrationConstants.SYSTEM_RESOURCE_PERMISSIONS;
    }

    @Override
    public String resourceName() {
        return "权限授权";
    }

    @Override
    public String resourceType() {
        return DataMigrationConstants.RESOURCE_TYPE_SYSTEM;
    }

    @Override
    public boolean attachmentSupported() {
        return false;
    }

    @Override
    public String entryPath() {
        return "system/permissions.json";
    }

    @Override
    public int order() {
        return 40;
    }

    @Override
    public MigrationResourceExportData exportData(ExportContext context) {
        QueryWrapper<UserAppPermission> wrapper = new QueryWrapper<>();
        wrapper.orderByAsc("user_id").orderByAsc("app_code").orderByAsc("id");
        List<UserAppPermission> permissions = permissionMapper.selectList(wrapper);
        return new MigrationResourceExportData(resourceCode(), entryPath(), new Payload(permissions), permissions.size(), 0L, List.of());
    }

    @Override
    @Transactional
    public MigrationResourceImportResult importData(ImportContext context) throws Exception {
        Payload payload = archiveService.readJson(context.packageRoot(), entryPath(), Payload.class);
        long importedCount = 0L;
        for (UserAppPermission source : payload.getPermissions()) {
            if (source == null || !StringUtils.hasText(source.getAppCode())) {
                continue;
            }
            Long targetUserId = resolveUserId(source.getUserId(), context);
            if (targetUserId == null) {
                throw new BusinessException("DATA_MIGRATION_PERMISSION_USER_MISSING", "授权依赖的用户不存在: " + source.getUserId());
            }
            SystemApp targetApp = resolveApp(source, context);
            if (targetApp == null) {
                throw new BusinessException("DATA_MIGRATION_PERMISSION_APP_MISSING", "授权依赖的应用不存在: " + source.getAppCode());
            }

            UserAppPermission existing = findExisting(targetUserId, targetApp.getAppCode());
            if (existing != null) {
                if (context.isStrict()) {
                    throw new BusinessException("DATA_MIGRATION_PERMISSION_CONFLICT",
                            "授权已存在: userId=" + targetUserId + ", appCode=" + targetApp.getAppCode());
                }
                source.setUserId(targetUserId);
                source.setAppId(targetApp.getId());
                source.setAppCode(targetApp.getAppCode());
                source.setGrantedBy(resolveOperatorId(source.getGrantedBy(), context));
                if (context.isOverwrite()) {
                    DataMigrationBeanMergeSupport.overwrite(source, existing, "id");
                } else {
                    DataMigrationBeanMergeSupport.mergeNonNull(source, existing, "id");
                }
                permissionMapper.updateById(existing);
                importedCount++;
                continue;
            }

            UserAppPermission insertPermission = copyPermission(source);
            insertPermission.setUserId(targetUserId);
            insertPermission.setAppId(targetApp.getId());
            insertPermission.setAppCode(targetApp.getAppCode());
            insertPermission.setGrantedBy(resolveOperatorId(source.getGrantedBy(), context));
            if (insertPermission.getId() != null && permissionMapper.selectById(insertPermission.getId()) != null) {
                if (context.isStrict()) {
                    throw new BusinessException("DATA_MIGRATION_PERMISSION_ID_CONFLICT", "授权 ID 冲突: " + insertPermission.getId());
                }
                insertPermission.setId(null);
            }
            permissionMapper.insert(insertPermission);
            importedCount++;
        }
        return MigrationResourceImportResult.success(importedCount, 0L, "权限授权导入完成");
    }

    private UserAppPermission findExisting(Long userId, String appCode) {
        QueryWrapper<UserAppPermission> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId).eq("app_code", appCode);
        return permissionMapper.selectOne(wrapper);
    }

    private Long resolveUserId(Long sourceUserId, ImportContext context) {
        Long mappedId = context.mappedUserId(sourceUserId);
        if (mappedId != null) {
            return mappedId;
        }
        User sameIdUser = sourceUserId == null ? null : userMapper.selectById(sourceUserId);
        return sameIdUser == null ? null : sameIdUser.getId();
    }

    private Long resolveOperatorId(Long sourceOperatorId, ImportContext context) {
        if (sourceOperatorId == null) {
            return null;
        }
        Long mappedId = context.mappedUserId(sourceOperatorId);
        if (mappedId != null) {
            return mappedId;
        }
        User sameIdUser = userMapper.selectById(sourceOperatorId);
        return sameIdUser == null ? null : sameIdUser.getId();
    }

    private SystemApp resolveApp(UserAppPermission source, ImportContext context) {
        Long mappedId = context.mappedAppId(source.getAppId());
        if (mappedId != null) {
            SystemApp app = systemAppMapper.selectById(mappedId);
            if (app != null) {
                return app;
            }
        }
        QueryWrapper<SystemApp> wrapper = new QueryWrapper<>();
        wrapper.eq("app_code", source.getAppCode());
        SystemApp app = systemAppMapper.selectOne(wrapper);
        if (app != null) {
            context.mapAppId(source.getAppId(), app.getId());
        }
        return app;
    }

    private UserAppPermission copyPermission(UserAppPermission source) {
        UserAppPermission permission = new UserAppPermission();
        DataMigrationBeanMergeSupport.overwrite(source, permission);
        return permission;
    }

    /**
     * 权限导出载荷。
     */
    public static class Payload {

        private List<UserAppPermission> permissions;

        public Payload() {
        }

        public Payload(List<UserAppPermission> permissions) {
            this.permissions = permissions;
        }

        public List<UserAppPermission> getPermissions() {
            return permissions;
        }

        public void setPermissions(List<UserAppPermission> permissions) {
            this.permissions = permissions;
        }
    }
}
