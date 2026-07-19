package com.gak.datamigration.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gak.attachment.constant.AttachmentConstants;
import com.gak.attachment.service.AttachmentService;
import com.gak.healthrecord.domain.HealthReport;
import com.gak.healthrecord.domain.HealthVisit;
import com.gak.healthrecord.mapper.HealthReportMapper;
import com.gak.healthrecord.mapper.HealthVisitMapper;
import com.gak.permission.domain.SystemApp;
import com.gak.permission.mapper.SystemAppMapper;
import com.gak.user.constant.UserSecurityConstants;
import com.gak.user.domain.user.User;
import com.gak.user.mapper.user.UserMapper;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 将旧健康附件和应用图标登记到统一附件表，保留原目录以降低上线迁移风险。
 */
@Component
public class LegacyAttachmentMigrationRunner implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(LegacyAttachmentMigrationRunner.class);

    private final AttachmentService attachmentService;
    private final HealthReportMapper healthReportMapper;
    private final HealthVisitMapper healthVisitMapper;
    private final SystemAppMapper systemAppMapper;
    private final UserMapper userMapper;
    private final boolean enabled;

    public LegacyAttachmentMigrationRunner(AttachmentService attachmentService,
                                           HealthReportMapper healthReportMapper,
                                           HealthVisitMapper healthVisitMapper,
                                           SystemAppMapper systemAppMapper,
                                           UserMapper userMapper,
                                           @Value("${gak.attachment.legacy-migration-enabled:true}") boolean enabled) {
        this.attachmentService = attachmentService;
        this.healthReportMapper = healthReportMapper;
        this.healthVisitMapper = healthVisitMapper;
        this.systemAppMapper = systemAppMapper;
        this.userMapper = userMapper;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) return;
        migrateHealthReports();
        migrateHealthVisits();
        migrateSystemAppIcons();
        LOGGER.info("旧附件元数据迁移检查完成");
    }

    private void migrateHealthReports() {
        for (HealthReport report : healthReportMapper.selectList(new QueryWrapper<>())) {
            if (!StringUtils.hasText(report.getReportFileName())) continue;
            attachmentService.registerLegacyAttachment(report.getOwnerUserId(),
                    AttachmentConstants.BUSINESS_HEALTH_REPORT, report.getId(), AttachmentConstants.USAGE_ATTACHMENT,
                    "health-records/" + report.getReportFileName(), report.getReportFileName(),
                    "HEALTH_REPORT:" + report.getId());
        }
    }

    private void migrateHealthVisits() {
        for (HealthVisit visit : healthVisitMapper.selectList(new QueryWrapper<>())) {
            if (!StringUtils.hasText(visit.getCaseRecordFileName())) continue;
            attachmentService.registerLegacyAttachment(visit.getOwnerUserId(),
                    AttachmentConstants.BUSINESS_HEALTH_VISIT, visit.getId(), AttachmentConstants.USAGE_ATTACHMENT,
                    "health-records/" + visit.getCaseRecordFileName(), visit.getCaseRecordFileName(),
                    "HEALTH_VISIT:" + visit.getId());
        }
    }

    private void migrateSystemAppIcons() {
        Long adminUserId = findAdminUserId();
        if (adminUserId == null) return;
        for (SystemApp app : systemAppMapper.selectList(new QueryWrapper<>())) {
            if (!StringUtils.hasText(app.getIconFileName())) continue;
            attachmentService.registerLegacyAttachment(adminUserId,
                    AttachmentConstants.BUSINESS_SYSTEM_APP, app.getId(), AttachmentConstants.USAGE_ICON,
                    "app-icons/" + app.getIconFileName(), app.getIconFileName(), "SYSTEM_APP:" + app.getId());
        }
    }

    private Long findAdminUserId() {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("role_code", UserSecurityConstants.ADMIN_ROLE_CODE).orderByAsc("id").last("LIMIT 1");
        List<User> admins = userMapper.selectList(wrapper);
        return admins.isEmpty() ? null : admins.get(0).getId();
    }
}
