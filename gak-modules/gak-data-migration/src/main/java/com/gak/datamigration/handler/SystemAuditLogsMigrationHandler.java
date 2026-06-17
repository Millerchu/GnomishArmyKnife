package com.gak.datamigration.handler;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gak.datamigration.DataMigrationConstants;
import com.gak.datamigration.service.DataMigrationArchiveService;
import com.gak.datamigration.service.DataMigrationBeanMergeSupport;
import com.gak.datamigration.service.DataMigrationQuerySupport;
import com.gak.permission.domain.AppAuditLog;
import com.gak.permission.domain.PermissionAuditLog;
import com.gak.permission.mapper.AppAuditLogMapper;
import com.gak.permission.mapper.PermissionAuditLogMapper;
import com.gak.user.domain.user.User;
import com.gak.user.mapper.user.UserMapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 系统审计日志迁移处理器。
 */
@Service
public class SystemAuditLogsMigrationHandler implements MigrationResourceHandler {

    private final PermissionAuditLogMapper permissionAuditLogMapper;
    private final AppAuditLogMapper appAuditLogMapper;
    private final UserMapper userMapper;
    private final DataMigrationArchiveService archiveService;

    public SystemAuditLogsMigrationHandler(PermissionAuditLogMapper permissionAuditLogMapper,
                                           AppAuditLogMapper appAuditLogMapper,
                                           UserMapper userMapper,
                                           DataMigrationArchiveService archiveService) {
        this.permissionAuditLogMapper = permissionAuditLogMapper;
        this.appAuditLogMapper = appAuditLogMapper;
        this.userMapper = userMapper;
        this.archiveService = archiveService;
    }

    @Override
    public String resourceCode() {
        return DataMigrationConstants.SYSTEM_RESOURCE_AUDIT_LOGS;
    }

    @Override
    public String resourceName() {
        return "系统审计日志";
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
        return "system/audit-logs.json";
    }

    @Override
    public int order() {
        return 50;
    }

    @Override
    public MigrationResourceExportData exportData(ExportContext context) {
        QueryWrapper<PermissionAuditLog> permissionWrapper = new QueryWrapper<>();
        permissionWrapper.orderByAsc("created_at").orderByAsc("id");
        List<PermissionAuditLog> permissionLogs = permissionAuditLogMapper.selectList(permissionWrapper);

        QueryWrapper<AppAuditLog> appWrapper = new QueryWrapper<>();
        appWrapper.orderByAsc("created_at").orderByAsc("id");
        List<AppAuditLog> appLogs = appAuditLogMapper.selectList(appWrapper);

        long recordCount = (long) permissionLogs.size() + appLogs.size();
        return new MigrationResourceExportData(resourceCode(), entryPath(), new Payload(permissionLogs, appLogs), recordCount, 0L, List.of());
    }

    @Override
    @Transactional
    public MigrationResourceImportResult importData(ImportContext context) throws Exception {
        Payload payload = archiveService.readJson(context.packageRoot(), entryPath(), Payload.class);
        long importedCount = importPermissionLogs(context, DataMigrationQuerySupport.emptyIfNull(payload.getPermissionLogs()));
        importedCount += importAppLogs(context, DataMigrationQuerySupport.emptyIfNull(payload.getAppLogs()));
        return MigrationResourceImportResult.success(importedCount, 0L, "系统审计日志导入完成");
    }

    private long importPermissionLogs(ImportContext context, List<PermissionAuditLog> logs) {
        long importedCount = 0L;
        for (PermissionAuditLog source : logs) {
            if (source == null || existsPermissionLog(source)) {
                continue;
            }
            PermissionAuditLog insertLog = new PermissionAuditLog();
            DataMigrationBeanMergeSupport.overwrite(source, insertLog);
            insertLog.setOperatorUserId(resolveOptionalUserId(source.getOperatorUserId(), context));
            insertLog.setTargetUserId(resolveOptionalUserId(source.getTargetUserId(), context));
            permissionAuditLogMapper.insert(insertLog);
            importedCount++;
        }
        return importedCount;
    }

    private long importAppLogs(ImportContext context, List<AppAuditLog> logs) {
        long importedCount = 0L;
        for (AppAuditLog source : logs) {
            if (source == null || existsAppLog(source)) {
                continue;
            }
            AppAuditLog insertLog = new AppAuditLog();
            DataMigrationBeanMergeSupport.overwrite(source, insertLog);
            insertLog.setOperatorUserId(resolveOptionalUserId(source.getOperatorUserId(), context));
            Long mappedAppId = context.mappedAppId(source.getAppId());
            insertLog.setAppId(mappedAppId != null ? mappedAppId : source.getAppId());
            appAuditLogMapper.insert(insertLog);
            importedCount++;
        }
        return importedCount;
    }

    private boolean existsPermissionLog(PermissionAuditLog source) {
        if (source.getId() != null && permissionAuditLogMapper.selectById(source.getId()) != null) {
            return true;
        }
        if (!StringUtils.hasText(source.getTraceId())) {
            return false;
        }
        QueryWrapper<PermissionAuditLog> wrapper = new QueryWrapper<>();
        wrapper.eq("trace_id", source.getTraceId()).eq("action_type", source.getActionType());
        return permissionAuditLogMapper.selectOne(wrapper) != null;
    }

    private boolean existsAppLog(AppAuditLog source) {
        if (source.getId() != null && appAuditLogMapper.selectById(source.getId()) != null) {
            return true;
        }
        QueryWrapper<AppAuditLog> wrapper = new QueryWrapper<>();
        wrapper.eq("created_at", source.getCreatedAt())
                .eq("action_type", source.getActionType());
        DataMigrationQuerySupport.eqNullable(wrapper, "app_id", source.getAppId());
        return appAuditLogMapper.selectOne(wrapper) != null;
    }

    private Long resolveOptionalUserId(Long sourceUserId, ImportContext context) {
        Long mappedId = context.mappedUserId(sourceUserId);
        if (mappedId != null) {
            return mappedId;
        }
        User sameUser = sourceUserId == null ? null : userMapper.selectById(sourceUserId);
        return sameUser == null ? sourceUserId : sameUser.getId();
    }

    /**
     * 系统审计日志导出载荷。
     */
    public static class Payload {

        private List<PermissionAuditLog> permissionLogs;
        private List<AppAuditLog> appLogs;

        public Payload() {
        }

        public Payload(List<PermissionAuditLog> permissionLogs, List<AppAuditLog> appLogs) {
            this.permissionLogs = permissionLogs;
            this.appLogs = appLogs;
        }

        public List<PermissionAuditLog> getPermissionLogs() {
            return permissionLogs;
        }

        public void setPermissionLogs(List<PermissionAuditLog> permissionLogs) {
            this.permissionLogs = permissionLogs;
        }

        public List<AppAuditLog> getAppLogs() {
            return appLogs;
        }

        public void setAppLogs(List<AppAuditLog> appLogs) {
            this.appLogs = appLogs;
        }
    }
}
