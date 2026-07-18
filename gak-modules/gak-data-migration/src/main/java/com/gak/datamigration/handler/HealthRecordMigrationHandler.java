package com.gak.datamigration.handler;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gak.datamigration.DataMigrationConstants;
import com.gak.datamigration.service.DataMigrationArchiveService;
import com.gak.datamigration.service.DataMigrationBeanMergeSupport;
import com.gak.datamigration.service.DataMigrationQuerySupport;
import com.gak.datamigration.service.AttachmentMigrationSupport;
import com.gak.datamigration.service.AttachmentMigrationSupport.ExportBundle;
import com.gak.datamigration.service.AttachmentMigrationSupport.TransferItem;
import com.gak.attachment.constant.AttachmentConstants;
import com.gak.framework.exception.BusinessException;
import com.gak.healthrecord.domain.HealthRecord;
import com.gak.healthrecord.domain.HealthReport;
import com.gak.healthrecord.domain.HealthVisit;
import com.gak.healthrecord.mapper.HealthRecordMapper;
import com.gak.healthrecord.mapper.HealthReportMapper;
import com.gak.healthrecord.mapper.HealthVisitMapper;
import com.gak.user.domain.user.User;
import com.gak.user.mapper.user.UserMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 健康记录迁移处理器。
 */
@Service
public class HealthRecordMigrationHandler implements MigrationResourceHandler {

    private static final String ATTACHMENT_DIR = "attachments/health-records";

    private final HealthRecordMapper healthRecordMapper;
    private final HealthVisitMapper healthVisitMapper;
    private final HealthReportMapper healthReportMapper;
    private final UserMapper userMapper;
    private final DataMigrationArchiveService archiveService;
    private final Path localStorageDir;
    private final String publicUrlPrefix;
    private final AttachmentMigrationSupport attachmentMigrationSupport;

    public HealthRecordMigrationHandler(HealthRecordMapper healthRecordMapper,
                                        HealthVisitMapper healthVisitMapper,
                                        HealthReportMapper healthReportMapper,
                                        UserMapper userMapper,
                                        DataMigrationArchiveService archiveService,
                                        AttachmentMigrationSupport attachmentMigrationSupport,
                                        @Value("${gak.health.file.local-dir:./data/health-records}") String localDir,
                                        @Value("${gak.health.file.public-url-prefix:/api/health-records/report-files/}") String publicUrlPrefix) {
        this.healthRecordMapper = healthRecordMapper;
        this.healthVisitMapper = healthVisitMapper;
        this.healthReportMapper = healthReportMapper;
        this.userMapper = userMapper;
        this.archiveService = archiveService;
        this.attachmentMigrationSupport = attachmentMigrationSupport;
        this.localStorageDir = Paths.get(localDir == null ? "./data/health-records" : localDir).toAbsolutePath().normalize();
        this.publicUrlPrefix = publicUrlPrefix != null && publicUrlPrefix.endsWith("/") ? publicUrlPrefix : publicUrlPrefix + "/";
    }

    @Override
    public String resourceCode() {
        return DataMigrationConstants.APP_HEALTH_RECORD;
    }

    @Override
    public String resourceName() {
        return "健康记录";
    }

    @Override
    public String resourceType() {
        return DataMigrationConstants.RESOURCE_TYPE_BUSINESS;
    }

    @Override
    public boolean attachmentSupported() {
        return true;
    }

    @Override
    public String entryPath() {
        return "business/" + resourceCode() + "/data.json";
    }

    @Override
    public int order() {
        return 170;
    }

    @Override
    public MigrationResourceExportData exportData(ExportContext context) {
        QueryWrapper<HealthRecord> recordWrapper = new QueryWrapper<>();
        recordWrapper.orderByAsc("owner_user_id").orderByAsc("measure_date").orderByAsc("id");
        List<HealthRecord> records = healthRecordMapper.selectList(recordWrapper);

        QueryWrapper<HealthVisit> visitWrapper = new QueryWrapper<>();
        visitWrapper.orderByAsc("owner_user_id").orderByAsc("visit_date").orderByAsc("id");
        List<HealthVisit> visits = healthVisitMapper.selectList(visitWrapper);

        QueryWrapper<HealthReport> reportWrapper = new QueryWrapper<>();
        reportWrapper.orderByAsc("owner_user_id").orderByAsc("exam_date").orderByAsc("id");
        List<HealthReport> reports = healthReportMapper.selectList(reportWrapper);

        ExportBundle visitBundle = context.includeAttachments()
                ? attachmentMigrationSupport.collect(AttachmentConstants.BUSINESS_HEALTH_VISIT,
                visits.stream().map(HealthVisit::getId).toList(), "attachments/health-visits")
                : new ExportBundle(List.of(), List.of());
        ExportBundle reportBundle = context.includeAttachments()
                ? attachmentMigrationSupport.collect(AttachmentConstants.BUSINESS_HEALTH_REPORT,
                reports.stream().map(HealthReport::getId).toList(), "attachments/health-reports")
                : new ExportBundle(List.of(), List.of());
        List<MigrationAttachment> attachments = new ArrayList<>(visitBundle.files());
        attachments.addAll(reportBundle.files());
        long recordCount = (long) records.size() + visits.size() + reports.size();
        return new MigrationResourceExportData(resourceCode(), entryPath(),
                new Payload(records, visits, reports, visitBundle.items(), reportBundle.items()),
                recordCount, attachments.size(), attachments);
    }

    @Override
    @Transactional
    public MigrationResourceImportResult importData(ImportContext context) throws Exception {
        Payload payload = archiveService.readJson(context.packageRoot(), entryPath(), Payload.class);
        Map<Long, Long> visitIdMappings = new LinkedHashMap<>();
        Map<Long, Long> reportIdMappings = new LinkedHashMap<>();
        long importedCount = importRecords(context, DataMigrationQuerySupport.emptyIfNull(payload.getRecords()));
        importedCount += importVisits(context, DataMigrationQuerySupport.emptyIfNull(payload.getVisits()), visitIdMappings);
        importedCount += importReports(context, DataMigrationQuerySupport.emptyIfNull(payload.getReports()),
                visitIdMappings, reportIdMappings);
        long attachmentCount = attachmentMigrationSupport.restore(context,
                AttachmentConstants.BUSINESS_HEALTH_VISIT,
                DataMigrationQuerySupport.emptyIfNull(payload.getVisitAttachments()), visitIdMappings,
                targetId -> {
                    HealthVisit visit = healthVisitMapper.selectById(targetId);
                    return visit == null ? null : visit.getOwnerUserId();
                }, 10);
        attachmentCount += attachmentMigrationSupport.restore(context,
                AttachmentConstants.BUSINESS_HEALTH_REPORT,
                DataMigrationQuerySupport.emptyIfNull(payload.getReportAttachments()), reportIdMappings,
                targetId -> {
                    HealthReport report = healthReportMapper.selectById(targetId);
                    return report == null ? null : report.getOwnerUserId();
                }, 10);
        if (context.includeAttachments() && payload.getVisitAttachments() == null && payload.getReportAttachments() == null) {
            attachmentCount += restoreAttachments(context, DataMigrationQuerySupport.emptyIfNull(payload.getVisits()),
                    DataMigrationQuerySupport.emptyIfNull(payload.getReports()));
        }
        return MigrationResourceImportResult.success(importedCount, attachmentCount, "健康记录导入完成");
    }

    private List<MigrationAttachment> collectAttachments(List<HealthVisit> visits, List<HealthReport> reports) {
        List<MigrationAttachment> attachments = new ArrayList<>();
        for (HealthVisit visit : DataMigrationQuerySupport.emptyIfNull(visits)) {
            addAttachment(attachments, visit == null ? null : visit.getCaseRecordFileName());
        }
        for (HealthReport report : DataMigrationQuerySupport.emptyIfNull(reports)) {
            addAttachment(attachments, report == null ? null : report.getReportFileName());
        }
        return attachments;
    }

    private void addAttachment(List<MigrationAttachment> attachments, String fileName) {
        String normalized = normalizeFileName(fileName);
        if (normalized == null) {
            return;
        }
        Path sourcePath = localStorageDir.resolve(normalized).normalize();
        if (sourcePath.startsWith(localStorageDir) && Files.isRegularFile(sourcePath)) {
            attachments.add(new MigrationAttachment(ATTACHMENT_DIR + "/" + normalized, normalized, sourcePath));
        }
    }

    private long importRecords(ImportContext context, List<HealthRecord> records) {
        long importedCount = 0L;
        for (HealthRecord source : records) {
            if (source == null) {
                continue;
            }
            Long targetUserId = resolveUserId(source.getOwnerUserId(), context, "健康指标");
            HealthRecord existing = findExistingRecord(source, targetUserId);
            source.setOwnerUserId(targetUserId);
            if (existing != null) {
                mergeExisting(context, source, existing, "DATA_MIGRATION_HEALTH_RECORD_CONFLICT");
                existing.setOwnerUserId(targetUserId);
                healthRecordMapper.updateById(existing);
            } else {
                HealthRecord insertRecord = new HealthRecord();
                DataMigrationBeanMergeSupport.overwrite(source, insertRecord);
                insertRecord.setOwnerUserId(targetUserId);
                healthRecordMapper.insert(insertRecord);
            }
            importedCount++;
        }
        return importedCount;
    }

    private long importVisits(ImportContext context, List<HealthVisit> visits, Map<Long, Long> visitIdMappings) {
        long importedCount = 0L;
        for (HealthVisit source : visits) {
            if (source == null) {
                continue;
            }
            Long targetUserId = resolveUserId(source.getOwnerUserId(), context, "健康就诊");
            HealthVisit existing = findExistingVisit(source, targetUserId);
            source.setOwnerUserId(targetUserId);
            normalizeVisitAttachmentUrl(source);
            if (existing != null) {
                mergeExisting(context, source, existing, "DATA_MIGRATION_HEALTH_VISIT_CONFLICT");
                existing.setOwnerUserId(targetUserId);
                healthVisitMapper.updateById(existing);
                visitIdMappings.put(source.getId(), existing.getId());
            } else {
                HealthVisit insertVisit = new HealthVisit();
                DataMigrationBeanMergeSupport.overwrite(source, insertVisit);
                insertVisit.setOwnerUserId(targetUserId);
                healthVisitMapper.insert(insertVisit);
                visitIdMappings.put(source.getId(), insertVisit.getId());
            }
            importedCount++;
        }
        return importedCount;
    }

    private long importReports(ImportContext context,
                               List<HealthReport> reports,
                               Map<Long, Long> visitIdMappings,
                               Map<Long, Long> reportIdMappings) {
        long importedCount = 0L;
        for (HealthReport source : reports) {
            if (source == null) {
                continue;
            }
            Long sourceReportId = source.getId();
            Long targetUserId = resolveUserId(source.getOwnerUserId(), context, "健康报告");
            Long targetVisitId = source.getVisitId() == null ? null : visitIdMappings.getOrDefault(source.getVisitId(), source.getVisitId());
            HealthReport existing = findExistingReport(source, targetUserId, targetVisitId);
            source.setOwnerUserId(targetUserId);
            source.setVisitId(targetVisitId);
            normalizeReportAttachmentUrl(source);
            if (existing != null) {
                mergeExisting(context, source, existing, "DATA_MIGRATION_HEALTH_REPORT_CONFLICT");
                existing.setOwnerUserId(targetUserId);
                existing.setVisitId(targetVisitId);
                healthReportMapper.updateById(existing);
                reportIdMappings.put(sourceReportId, existing.getId());
            } else {
                HealthReport insertReport = new HealthReport();
                DataMigrationBeanMergeSupport.overwrite(source, insertReport);
                insertReport.setOwnerUserId(targetUserId);
                insertReport.setVisitId(targetVisitId);
                healthReportMapper.insert(insertReport);
                reportIdMappings.put(sourceReportId, insertReport.getId());
            }
            importedCount++;
        }
        return importedCount;
    }

    private void mergeExisting(ImportContext context, Object source, Object existing, String conflictCode) {
        if (context.isStrict()) {
            throw new BusinessException(conflictCode, "健康记录已存在");
        }
        if (context.isOverwrite()) {
            DataMigrationBeanMergeSupport.overwriteNewest(source, existing, "id", "ownerUserId");
        } else {
            DataMigrationBeanMergeSupport.mergeNewestNonNull(source, existing, "id", "ownerUserId");
        }
    }

    private long restoreAttachments(ImportContext context, List<HealthVisit> visits, List<HealthReport> reports) throws IOException {
        long restoredCount = 0L;
        for (HealthVisit visit : visits) {
            restoredCount += restoreAttachment(context, visit == null ? null : visit.getCaseRecordFileName());
        }
        for (HealthReport report : reports) {
            restoredCount += restoreAttachment(context, report == null ? null : report.getReportFileName());
        }
        return restoredCount;
    }

    private long restoreAttachment(ImportContext context, String fileName) throws IOException {
        String normalized = normalizeFileName(fileName);
        if (normalized == null) {
            return 0L;
        }
        Path sourcePath = context.attachmentPath(ATTACHMENT_DIR + "/" + normalized);
        if (!Files.isRegularFile(sourcePath)) {
            return 0L;
        }
        Files.createDirectories(localStorageDir);
        Path targetPath = localStorageDir.resolve(normalized).normalize();
        if (!targetPath.startsWith(localStorageDir)) {
            throw new BusinessException("DATA_MIGRATION_HEALTH_ATTACHMENT_INVALID", "健康附件路径非法: " + normalized);
        }
        Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        return 1L;
    }

    private HealthRecord findExistingRecord(HealthRecord source, Long targetUserId) {
        HealthRecord byId = source.getId() == null ? null : healthRecordMapper.selectById(source.getId());
        if (byId != null) {
            return byId;
        }
        QueryWrapper<HealthRecord> wrapper = new QueryWrapper<>();
        wrapper.eq("owner_user_id", targetUserId).eq("measure_date", source.getMeasureDate());
        return healthRecordMapper.selectOne(wrapper);
    }

    private HealthVisit findExistingVisit(HealthVisit source, Long targetUserId) {
        HealthVisit byId = source.getId() == null ? null : healthVisitMapper.selectById(source.getId());
        if (byId != null) {
            return byId;
        }
        QueryWrapper<HealthVisit> wrapper = new QueryWrapper<>();
        wrapper.eq("owner_user_id", targetUserId).eq("visit_date", source.getVisitDate());
        DataMigrationQuerySupport.eqNullable(wrapper, "hospital_name", source.getHospitalName());
        DataMigrationQuerySupport.eqNullable(wrapper, "department_name", source.getDepartmentName());
        DataMigrationQuerySupport.eqNullable(wrapper, "doctor_name", source.getDoctorName());
        return healthVisitMapper.selectOne(wrapper);
    }

    private HealthReport findExistingReport(HealthReport source, Long targetUserId, Long targetVisitId) {
        HealthReport byId = source.getId() == null ? null : healthReportMapper.selectById(source.getId());
        if (byId != null) {
            return byId;
        }
        QueryWrapper<HealthReport> wrapper = new QueryWrapper<>();
        wrapper.eq("owner_user_id", targetUserId).eq("exam_date", source.getExamDate());
        DataMigrationQuerySupport.eqNullable(wrapper, "visit_id", targetVisitId);
        DataMigrationQuerySupport.eqNullable(wrapper, "hospital_name", source.getHospitalName());
        DataMigrationQuerySupport.eqNullable(wrapper, "report_title", source.getReportTitle());
        return healthReportMapper.selectOne(wrapper);
    }

    private Long resolveUserId(Long sourceUserId, ImportContext context, String resourceName) {
        Long mappedId = context.mappedUserId(sourceUserId);
        if (mappedId != null) {
            return mappedId;
        }
        User sameUser = sourceUserId == null ? null : userMapper.selectById(sourceUserId);
        if (sameUser == null) {
            throw new BusinessException("DATA_MIGRATION_HEALTH_USER_MISSING", resourceName + "依赖的用户不存在: " + sourceUserId);
        }
        return sameUser.getId();
    }

    private void normalizeVisitAttachmentUrl(HealthVisit visit) {
        String fileName = normalizeFileName(visit.getCaseRecordFileName());
        if (fileName != null) {
            visit.setCaseRecordFileName(fileName);
            visit.setCaseRecordUrl(publicUrlPrefix + fileName);
        }
    }

    private void normalizeReportAttachmentUrl(HealthReport report) {
        String fileName = normalizeFileName(report.getReportFileName());
        if (fileName != null) {
            report.setReportFileName(fileName);
            report.setReportUrl(publicUrlPrefix + fileName);
        }
    }

    private String normalizeFileName(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return null;
        }
        String normalized = fileName.trim();
        if (normalized.contains("..") || normalized.contains("/") || normalized.contains("\\")) {
            return null;
        }
        return normalized;
    }

    /**
     * 健康记录导出载荷。
     */
    public static class Payload {

        private List<HealthRecord> records;
        private List<HealthVisit> visits;
        private List<HealthReport> reports;
        private List<TransferItem> visitAttachments;
        private List<TransferItem> reportAttachments;

        public Payload() {
        }

        public Payload(List<HealthRecord> records,
                       List<HealthVisit> visits,
                       List<HealthReport> reports,
                       List<TransferItem> visitAttachments,
                       List<TransferItem> reportAttachments) {
            this.records = records;
            this.visits = visits;
            this.reports = reports;
            this.visitAttachments = visitAttachments;
            this.reportAttachments = reportAttachments;
        }

        public List<HealthRecord> getRecords() {
            return records;
        }

        public void setRecords(List<HealthRecord> records) {
            this.records = records;
        }

        public List<HealthVisit> getVisits() {
            return visits;
        }

        public void setVisits(List<HealthVisit> visits) {
            this.visits = visits;
        }

        public List<HealthReport> getReports() {
            return reports;
        }

        public void setReports(List<HealthReport> reports) {
            this.reports = reports;
        }

        public List<TransferItem> getVisitAttachments() { return visitAttachments; }
        public void setVisitAttachments(List<TransferItem> visitAttachments) { this.visitAttachments = visitAttachments; }
        public List<TransferItem> getReportAttachments() { return reportAttachments; }
        public void setReportAttachments(List<TransferItem> reportAttachments) { this.reportAttachments = reportAttachments; }
    }
}
