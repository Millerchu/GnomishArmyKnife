package com.gak.datamigration.handler;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gak.datamigration.DataMigrationConstants;
import com.gak.datamigration.service.DataMigrationArchiveService;
import com.gak.datamigration.service.DataMigrationBeanMergeSupport;
import com.gak.framework.exception.BusinessException;
import com.gak.permission.domain.SystemApp;
import com.gak.permission.enums.AppIconStorageType;
import com.gak.permission.mapper.SystemAppMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 系统应用目录迁移处理器。
 */
@Service
public class SystemAppsMigrationHandler implements MigrationResourceHandler {

    private final SystemAppMapper systemAppMapper;
    private final DataMigrationArchiveService archiveService;
    private final Path iconStorageDir;
    private final String publicUrlPrefix;

    public SystemAppsMigrationHandler(SystemAppMapper systemAppMapper,
                                      DataMigrationArchiveService archiveService,
                                      @Value("${gak.app.icon.local-dir:./data/app-icons}") String iconStorageDir,
                                      @Value("${gak.app.icon.public-url-prefix:/api/system/apps/icon-files/}") String publicUrlPrefix) {
        this.systemAppMapper = systemAppMapper;
        this.archiveService = archiveService;
        this.iconStorageDir = Paths.get(iconStorageDir).toAbsolutePath().normalize();
        this.publicUrlPrefix = publicUrlPrefix.endsWith("/") ? publicUrlPrefix : publicUrlPrefix + "/";
    }

    @Override
    public String resourceCode() {
        return DataMigrationConstants.SYSTEM_RESOURCE_APPS;
    }

    @Override
    public String resourceName() {
        return "应用目录";
    }

    @Override
    public String resourceType() {
        return DataMigrationConstants.RESOURCE_TYPE_SYSTEM;
    }

    @Override
    public boolean attachmentSupported() {
        return true;
    }

    @Override
    public String entryPath() {
        return "system/apps.json";
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public MigrationResourceExportData exportData(ExportContext context) {
        QueryWrapper<SystemApp> wrapper = new QueryWrapper<>();
        wrapper.orderByAsc("sort_no").orderByAsc("id");
        List<SystemApp> apps = systemAppMapper.selectList(wrapper);
        List<MigrationAttachment> attachments = new ArrayList<>();
        long attachmentCount = 0L;
        if (context.includeAttachments()) {
            for (SystemApp app : apps) {
                if (!StringUtils.hasText(app.getIconFileName())) {
                    continue;
                }
                Path iconFile = iconStorageDir.resolve(app.getIconFileName()).normalize();
                if (Files.exists(iconFile) && Files.isRegularFile(iconFile)) {
                    attachments.add(new MigrationAttachment(
                            DataMigrationConstants.APP_ICON_ATTACHMENT_DIR + "/" + app.getIconFileName(),
                            app.getIconFileName(),
                            iconFile
                    ));
                    attachmentCount++;
                }
            }
        }
        return new MigrationResourceExportData(resourceCode(), entryPath(), new Payload(apps), apps.size(), attachmentCount, attachments);
    }

    @Override
    @Transactional
    public MigrationResourceImportResult importData(ImportContext context) throws Exception {
        Payload payload = archiveService.readJson(context.packageRoot(), entryPath(), Payload.class);
        long importedCount = 0L;
        long attachmentCount = 0L;
        for (SystemApp source : payload.getApps()) {
            if (source == null || !StringUtils.hasText(source.getAppCode())) {
                continue;
            }
            SystemApp existing = findByAppCode(source.getAppCode());
            if (existing != null) {
                if (context.isStrict()) {
                    throw new BusinessException("DATA_MIGRATION_APP_CONFLICT", "应用已存在: " + source.getAppCode());
                }
                if (context.includeAttachments()) {
                    attachmentCount += restoreIcon(source, context);
                }
                if (context.isOverwrite()) {
                    DataMigrationBeanMergeSupport.overwrite(source, existing, "id", "appCode");
                } else {
                    DataMigrationBeanMergeSupport.mergeNewestNonNull(source, existing, "id", "appCode");
                }
                existing.setAppCode(source.getAppCode());
                systemAppMapper.updateById(existing);
                context.mapAppId(source.getId(), existing.getId());
                importedCount++;
                continue;
            }

            SystemApp insertApp = copyApp(source);
            if (context.includeAttachments()) {
                attachmentCount += restoreIcon(insertApp, context);
            }
            if (insertApp.getId() != null && systemAppMapper.selectById(insertApp.getId()) != null) {
                if (context.isStrict()) {
                    throw new BusinessException("DATA_MIGRATION_APP_ID_CONFLICT", "应用 ID 冲突: " + insertApp.getId());
                }
                insertApp.setId(null);
            }
            systemAppMapper.insert(insertApp);
            context.mapAppId(source.getId(), insertApp.getId());
            importedCount++;
        }
        return MigrationResourceImportResult.success(importedCount, attachmentCount, "应用目录导入完成");
    }

    private SystemApp findByAppCode(String appCode) {
        QueryWrapper<SystemApp> wrapper = new QueryWrapper<>();
        wrapper.eq("app_code", appCode.trim());
        return systemAppMapper.selectOne(wrapper);
    }

    private SystemApp copyApp(SystemApp source) {
        SystemApp app = new SystemApp();
        DataMigrationBeanMergeSupport.overwrite(source, app);
        return app;
    }

    private long restoreIcon(SystemApp app, ImportContext context) throws IOException {
        if (!StringUtils.hasText(app.getIconFileName())) {
            return 0L;
        }
        Path attachment = context.attachmentPath(DataMigrationConstants.APP_ICON_ATTACHMENT_DIR + "/" + app.getIconFileName());
        if (!Files.exists(attachment) || !Files.isRegularFile(attachment)) {
            throw new BusinessException("DATA_MIGRATION_ATTACHMENT_MISSING", "缺少应用图标附件: " + app.getIconFileName());
        }
        Files.createDirectories(iconStorageDir);
        Files.copy(attachment, iconStorageDir.resolve(app.getIconFileName()), StandardCopyOption.REPLACE_EXISTING);
        app.setIconStorageType(AppIconStorageType.FILE_SERVER.name());
        app.setIconUrl(publicUrlPrefix + app.getIconFileName());
        return 1L;
    }

    /**
     * 应用目录导出载荷。
     */
    public static class Payload {

        private List<SystemApp> apps;

        public Payload() {
        }

        public Payload(List<SystemApp> apps) {
            this.apps = apps;
        }

        public List<SystemApp> getApps() {
            return apps;
        }

        public void setApps(List<SystemApp> apps) {
            this.apps = apps;
        }
    }
}
