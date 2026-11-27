package com.gak.datamigration.handler;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gak.attachment.constant.AttachmentConstants;
import com.gak.datamigration.DataMigrationConstants;
import com.gak.datamigration.dto.HealthRecordMigrationPayload;
import com.gak.datamigration.dto.HealthReportDetailMigrationItem;
import com.gak.datamigration.service.AttachmentMigrationSupport;
import com.gak.datamigration.service.AttachmentMigrationSupport.ExportBundle;
import com.gak.datamigration.service.DataMigrationArchiveService;
import com.gak.datamigration.service.DataMigrationBeanMergeSupport;
import com.gak.datamigration.service.DataMigrationQuerySupport;
import com.gak.datamigration.service.HealthVisitBillMigrationSupport;
import com.gak.framework.exception.BusinessException;
import com.gak.healthrecord.domain.HealthRecord;
import com.gak.healthrecord.domain.HealthReport;
import com.gak.healthrecord.domain.HealthReportDetail;
import com.gak.healthrecord.domain.HealthReportDetailItem;
import com.gak.healthrecord.domain.HealthVisit;
import com.gak.healthrecord.mapper.HealthRecordMapper;
import com.gak.healthrecord.mapper.HealthReportDetailMapper;
import com.gak.healthrecord.mapper.HealthReportMapper;
import com.gak.healthrecord.mapper.HealthVisitMapper;
import com.gak.healthrecord.service.HealthReportDetailService;
import com.gak.personalbills.domain.PersonalBill;
import com.gak.user.domain.user.User;
import com.gak.user.mapper.user.UserMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
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
    private final HealthReportDetailMapper healthReportDetailMapper;
    private final HealthReportDetailService healthReportDetailService;
    private final UserMapper userMapper;
    private final DataMigrationArchiveService archiveService;
    private final Path localStorageDir;
    private final String publicUrlPrefix;
    private final AttachmentMigrationSupport attachmentMigrationSupport;
    private final HealthVisitBillMigrationSupport billMigrationSupport;

    public HealthRecordMigrationHandler(HealthRecordMapper healthRecordMapper,
                                        HealthVisitMapper healthVisitMapper,
                                        HealthReportMapper healthReportMapper,
                                        HealthReportDetailMapper healthReportDetailMapper,
                                        HealthReportDetailService healthReportDetailService,
                                        UserMapper userMapper,
                                        DataMigrationArchiveService archiveService,
                                        AttachmentMigrationSupport attachmentMigrationSupport,
                                        HealthVisitBillMigrationSupport billMigrationSupport,
                                        @Value("${gak.health.file.local-dir:./data/health-records}") String localDir,
                                        @Value("${gak.health.file.public-url-prefix:/api/health-records/report-files/}") String publicUrlPrefix) {
        this.healthRecordMapper = healthRecordMapper;
        this.healthVisitMapper = healthVisitMapper;
        this.healthReportMapper = healthReportMapper;
        this.healthReportDetailMapper = healthReportDetailMapper;
        this.healthReportDetailService = healthReportDetailService;
        this.userMapper = userMapper;
        this.archiveService = archiveService;
        this.attachmentMigrationSupport = attachmentMigrationSupport;
        this.billMigrationSupport = billMigrationSupport;
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

        List<HealthReportDetail> details = DataMigrationQuerySupport.emptyIfNull(healthReportDetailMapper.selectAllOrdered());
        List<HealthReportDetailMigrationItem> reportDetails = details.stream()
                .map(detail -> new HealthReportDetailMigrationItem(
                        detail.getReportId(),
                        detail.getRecordId(),
                        detail.getItems() == null ? List.of() : detail.getItems(),
                        detail.getUpdatedAt()))
                .toList();

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
        List<PersonalBill> bills = billMigrationSupport.exportBills();
        long recordCount = (long) records.size() + visits.size() + reports.size() + bills.size() + reportDetails.size();
        return new MigrationResourceExportData(resourceCode(), entryPath(),
                new HealthRecordMigrationPayload(records, visits, reports,
                        visitBundle.items(), reportBundle.items(), bills, reportDetails),
                recordCount, attachments.size(), attachments);
    }

    @Override
    @Transactional
    public MigrationResourceImportResult importData(ImportContext context) throws Exception {
        HealthRecordMigrationPayload payload = archiveService.readJson(
                context.packageRoot(), entryPath(), HealthRecordMigrationPayload.class);
        Map<Long, Long> recordIdMappings = new LinkedHashMap<>();
        Map<Long, Long> visitIdMappings = new LinkedHashMap<>();
        Map<Long, Long> reportIdMappings = new LinkedHashMap<>();

        long importedCount = importRecords(context,
                DataMigrationQuerySupport.emptyIfNull(payload.getRecords()), recordIdMappings);
        importedCount += importVisits(context,
                DataMigrationQuerySupport.emptyIfNull(payload.getVisits()), visitIdMappings);
        importedCount += importReports(context,
                DataMigrationQuerySupport.emptyIfNull(payload.getReports()), visitIdMappings, reportIdMappings);
        importedCount += importReportDetails(context, payload.getReportDetails(), reportIdMappings, recordIdMappings);

        List<PersonalBill> bills = DataMigrationQuerySupport.emptyIfNull(payload.getBills());
        billMigrationSupport.restore(bills, visitIdMappings,
                sourceUserId -> resolveUserId(sourceUserId, context, "健康账单"));
        importedCount += bills.size();

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

    private long importRecords(ImportContext context,
                               List<HealthRecord> records,
                               Map<Long, Long> recordIdMappings) {
        Map<RecordNaturalKey, Integer> sourceKeyCounts = new HashMap<>();
        List<Long> targetUserIds = new ArrayList<>();
        Map<Long, List<HealthRecord>> preExistingByUser = new HashMap<>();
        Map<RecordNaturalKey, List<HealthRecord>> preExistingByKey = new HashMap<>();

        for (HealthRecord source : records) {
            if (source == null) {
                continue;
            }
            Long targetUserId = resolveUserId(source.getOwnerUserId(), context, "健康指标");
            targetUserIds.add(targetUserId);
            RecordNaturalKey key = new RecordNaturalKey(targetUserId, source.getMeasureDate());
            sourceKeyCounts.merge(key, 1, Integer::sum);
            if (!preExistingByUser.containsKey(targetUserId)) {
                List<HealthRecord> existingForUser = DataMigrationQuerySupport.emptyIfNull(
                        healthRecordMapper.selectList(new QueryWrapper<HealthRecord>().eq("owner_user_id", targetUserId)));
                preExistingByUser.put(targetUserId, existingForUser);
                for (HealthRecord candidate : existingForUser) {
                    RecordNaturalKey candidateKey = new RecordNaturalKey(targetUserId, candidate.getMeasureDate());
                    preExistingByKey.computeIfAbsent(candidateKey, ignored -> new ArrayList<>()).add(candidate);
                }
            }
        }

        Set<Long> matchedTargetIds = new HashSet<>();
        long importedCount = 0L;
        int userIndex = 0;
        for (HealthRecord source : records) {
            if (source == null) {
                continue;
            }
            Long sourceRecordId = source.getId();
            Long targetUserId = targetUserIds.get(userIndex++);
            HealthRecord existing = matchExistingRecord(
                    source, targetUserId, sourceKeyCounts, preExistingByKey, matchedTargetIds);
            source.setOwnerUserId(targetUserId);
            Long targetRecordId;
            if (existing != null) {
                mergeExisting(context, source, existing, "DATA_MIGRATION_HEALTH_RECORD_CONFLICT");
                existing.setOwnerUserId(targetUserId);
                healthRecordMapper.updateById(existing);
                targetRecordId = existing.getId();
            } else {
                HealthRecord insertRecord = new HealthRecord();
                DataMigrationBeanMergeSupport.overwrite(source, insertRecord);
                insertRecord.setOwnerUserId(targetUserId);
                healthRecordMapper.insert(insertRecord);
                targetRecordId = insertRecord.getId();
            }
            if (sourceRecordId != null && targetRecordId != null) {
                recordIdMappings.put(sourceRecordId, targetRecordId);
            }
            importedCount++;
        }
        return importedCount;
    }

    private HealthRecord matchExistingRecord(HealthRecord source,
                                             Long targetUserId,
                                             Map<RecordNaturalKey, Integer> sourceKeyCounts,
                                             Map<RecordNaturalKey, List<HealthRecord>> preExistingByKey,
                                             Set<Long> matchedTargetIds) {
        HealthRecord byId = source.getId() == null ? null : healthRecordMapper.selectById(source.getId());
        if (byId != null) {
            if (!targetUserId.equals(byId.getOwnerUserId())) {
                throw new BusinessException("DATA_MIGRATION_HEALTH_RECORD_OWNER_CONFLICT", "健康指标 ID 与其他用户记录冲突");
            }
            if (!matchedTargetIds.add(byId.getId())) {
                throw new BusinessException("DATA_MIGRATION_HEALTH_RECORD_MATCH_CONFLICT", "健康指标目标记录重复匹配");
            }
            return byId;
        }
        RecordNaturalKey key = new RecordNaturalKey(targetUserId, source.getMeasureDate());
        List<HealthRecord> candidates = preExistingByKey.getOrDefault(key, List.of());
        if (candidates.isEmpty()) {
            return null;
        }
        int sourceCount = sourceKeyCounts.getOrDefault(key, 0);
        if (sourceCount == 1 && candidates.size() == 1 && !matchedTargetIds.contains(candidates.get(0).getId())) {
            HealthRecord matched = candidates.get(0);
            matchedTargetIds.add(matched.getId());
            return matched;
        }
        throw new BusinessException("DATA_MIGRATION_HEALTH_RECORD_MATCH_CONFLICT",
                "同日存在多条健康指标记录且无法按 ID 精确匹配: " + source.getMeasureDate());
    }

    private long importVisits(ImportContext context, List<HealthVisit> visits, Map<Long, Long> visitIdMappings) {
        long importedCount = 0L;
        for (HealthVisit source : visits) {
            if (source == null) {
                continue;
            }
            Long sourceVisitId = source.getId();
            Long targetUserId = resolveUserId(source.getOwnerUserId(), context, "健康就诊");
            HealthVisit existing = findExistingVisit(source, targetUserId);
            source.setOwnerUserId(targetUserId);
            normalizeVisitAttachmentUrl(source);
            if (existing != null) {
                mergeExisting(context, source, existing, "DATA_MIGRATION_HEALTH_VISIT_CONFLICT");
                existing.setOwnerUserId(targetUserId);
                healthVisitMapper.updateById(existing);
                visitIdMappings.put(sourceVisitId, existing.getId());
            } else {
                HealthVisit insertVisit = new HealthVisit();
                DataMigrationBeanMergeSupport.overwrite(source, insertVisit);
                insertVisit.setOwnerUserId(targetUserId);
                healthVisitMapper.insert(insertVisit);
                visitIdMappings.put(sourceVisitId, insertVisit.getId());
            }
            importedCount++;
        }
        return importedCount;
    }

    private long importReports(ImportContext context,
                               List<HealthReport> reports,
                               Map<Long, Long> visitIdMappings,
                               Map<Long, Long> reportIdMappings) {
        Map<ReportNaturalKey, Integer> sourceKeyCounts = new HashMap<>();
        List<Long> targetUserIds = new ArrayList<>();
        List<Long> targetVisitIds = new ArrayList<>();
        Map<Long, List<HealthReport>> preExistingByUser = new HashMap<>();
        Map<ReportNaturalKey, List<HealthReport>> preExistingByKey = new HashMap<>();

        for (HealthReport source : reports) {
            if (source == null) {
                continue;
            }
            Long targetUserId = resolveUserId(source.getOwnerUserId(), context, "健康报告");
            Long targetVisitId = source.getVisitId() == null
                    ? null
                    : visitIdMappings.getOrDefault(source.getVisitId(), source.getVisitId());
            targetUserIds.add(targetUserId);
            targetVisitIds.add(targetVisitId);
            ReportNaturalKey key = new ReportNaturalKey(
                    targetUserId, source.getExamDate(), targetVisitId, source.getHospitalName(), source.getReportTitle());
            sourceKeyCounts.merge(key, 1, Integer::sum);
            if (!preExistingByUser.containsKey(targetUserId)) {
                List<HealthReport> existingForUser = DataMigrationQuerySupport.emptyIfNull(
                        healthReportMapper.selectList(new QueryWrapper<HealthReport>().eq("owner_user_id", targetUserId)));
                preExistingByUser.put(targetUserId, existingForUser);
                for (HealthReport candidate : existingForUser) {
                    ReportNaturalKey candidateKey = new ReportNaturalKey(
                            targetUserId, candidate.getExamDate(), candidate.getVisitId(),
                            candidate.getHospitalName(), candidate.getReportTitle());
                    preExistingByKey.computeIfAbsent(candidateKey, ignored -> new ArrayList<>()).add(candidate);
                }
            }
        }

        Set<Long> matchedTargetIds = new HashSet<>();
        long importedCount = 0L;
        int index = 0;
        for (HealthReport source : reports) {
            if (source == null) {
                continue;
            }
            Long sourceReportId = source.getId();
            Long targetUserId = targetUserIds.get(index);
            Long targetVisitId = targetVisitIds.get(index);
            index++;
            HealthReport existing = matchExistingReport(
                    source, targetUserId, targetVisitId, sourceKeyCounts, preExistingByKey, matchedTargetIds);
            source.setOwnerUserId(targetUserId);
            source.setVisitId(targetVisitId);
            normalizeReportAttachmentUrl(source);
            Long targetReportId;
            if (existing != null) {
                mergeExisting(context, source, existing, "DATA_MIGRATION_HEALTH_REPORT_CONFLICT");
                existing.setOwnerUserId(targetUserId);
                existing.setVisitId(targetVisitId);
                healthReportMapper.updateById(existing);
                targetReportId = existing.getId();
            } else {
                HealthReport insertReport = new HealthReport();
                DataMigrationBeanMergeSupport.overwrite(source, insertReport);
                insertReport.setOwnerUserId(targetUserId);
                insertReport.setVisitId(targetVisitId);
                healthReportMapper.insert(insertReport);
                targetReportId = insertReport.getId();
            }
            if (sourceReportId != null && targetReportId != null) {
                reportIdMappings.put(sourceReportId, targetReportId);
            }
            importedCount++;
        }
        return importedCount;
    }

    private HealthReport matchExistingReport(HealthReport source,
                                             Long targetUserId,
                                             Long targetVisitId,
                                             Map<ReportNaturalKey, Integer> sourceKeyCounts,
                                             Map<ReportNaturalKey, List<HealthReport>> preExistingByKey,
                                             Set<Long> matchedTargetIds) {
        HealthReport byId = source.getId() == null ? null : healthReportMapper.selectById(source.getId());
        if (byId != null) {
            if (!targetUserId.equals(byId.getOwnerUserId())) {
                throw new BusinessException("DATA_MIGRATION_HEALTH_REPORT_OWNER_CONFLICT", "健康报告 ID 与其他用户记录冲突");
            }
            if (!matchedTargetIds.add(byId.getId())) {
                throw new BusinessException("DATA_MIGRATION_HEALTH_REPORT_MATCH_CONFLICT", "健康报告目标记录重复匹配");
            }
            return byId;
        }
        ReportNaturalKey key = new ReportNaturalKey(
                targetUserId, source.getExamDate(), targetVisitId, source.getHospitalName(), source.getReportTitle());
        List<HealthReport> candidates = preExistingByKey.getOrDefault(key, List.of());
        if (candidates.isEmpty()) {
            return null;
        }
        int sourceCount = sourceKeyCounts.getOrDefault(key, 0);
        if (sourceCount == 1 && candidates.size() == 1 && !matchedTargetIds.contains(candidates.get(0).getId())) {
            HealthReport matched = candidates.get(0);
            matchedTargetIds.add(matched.getId());
            return matched;
        }
        throw new BusinessException("DATA_MIGRATION_HEALTH_REPORT_MATCH_CONFLICT",
                "存在多份同自然键健康报告且无法按 ID 精确匹配: " + source.getExamDate());
    }

    private long importReportDetails(ImportContext context,
                                     List<HealthReportDetailMigrationItem> reportDetails,
                                     Map<Long, Long> reportIdMappings,
                                     Map<Long, Long> recordIdMappings) {
        if (reportDetails == null) {
            return 0L;
        }
        Set<Long> seenSourceReportIds = new HashSet<>();
        for (HealthReportDetailMigrationItem source : reportDetails) {
            if (source == null || source.getReportId() == null) {
                throw new BusinessException("DATA_MIGRATION_HEALTH_REPORT_DETAIL_INVALID", "报告明细缺少 reportId");
            }
            if (!seenSourceReportIds.add(source.getReportId())) {
                throw new BusinessException("DATA_MIGRATION_HEALTH_REPORT_DETAIL_DUPLICATE", "迁移包中存在重复的报告明细");
            }
        }
        Set<Long> seenTargetReportIds = new HashSet<>();
        long importedCount = 0L;

        for (HealthReportDetailMigrationItem source : reportDetails) {
            Long targetReportId = reportIdMappings.get(source.getReportId());
            if (targetReportId == null) {
                throw new BusinessException("DATA_MIGRATION_HEALTH_REPORT_DETAIL_MAPPING_MISSING", "报告明细缺少对应的报告 ID 映射");
            }
            if (!seenTargetReportIds.add(targetReportId)) {
                throw new BusinessException("DATA_MIGRATION_HEALTH_REPORT_DETAIL_DUPLICATE", "同一目标报告存在重复的明细条目");
            }

            HealthReport targetReport = healthReportMapper.selectById(targetReportId);
            if (targetReport == null) {
                throw new BusinessException("DATA_MIGRATION_HEALTH_REPORT_DETAIL_MAPPING_MISSING", "报告明细对应的目标报告不存在");
            }

            Long targetRecordId = null;
            if (source.getRecordId() != null) {
                targetRecordId = recordIdMappings.get(source.getRecordId());
                if (targetRecordId == null) {
                    throw new BusinessException("DATA_MIGRATION_HEALTH_REPORT_DETAIL_MAPPING_MISSING", "报告明细缺少对应的核心指标 ID 映射");
                }
                HealthRecord targetRecord = healthRecordMapper.selectById(targetRecordId);
                if (targetRecord == null || !targetReport.getOwnerUserId().equals(targetRecord.getOwnerUserId())) {
                    throw new BusinessException("DATA_MIGRATION_HEALTH_REPORT_DETAIL_OWNER_MISMATCH",
                            "报告明细关联的报告与核心指标不属于同一用户");
                }
            }

            List<HealthReportDetailItem> normalizedItems = healthReportDetailService.validateAndNormalizeStoredItems(
                    source.getItems());

            HealthReportDetail existing = healthReportDetailMapper.findByReportId(targetReportId);
            if (existing == null) {
                ensureRecordNotLinkedByOtherReport(targetReportId, targetRecordId);
                HealthReportDetail toInsert = new HealthReportDetail();
                toInsert.setReportId(targetReportId);
                toInsert.setRecordId(targetRecordId);
                toInsert.setRevision(1);
                toInsert.setItems(normalizedItems);
                toInsert.setUpdatedAt(source.getUpdatedAt() != null ? source.getUpdatedAt() : LocalDateTime.now());
                try {
                    healthReportDetailMapper.insertDetail(toInsert);
                } catch (DataIntegrityViolationException exception) {
                    throw new BusinessException("HEALTH_RECORD_ALREADY_LINKED", "该核心指标记录已关联其他体检报告");
                }
            } else {
                if (context.isStrict()) {
                    throw new BusinessException("DATA_MIGRATION_HEALTH_REPORT_DETAIL_CONFLICT", "报告明细已存在");
                }
                boolean sourceIsNewer = source.getUpdatedAt() != null
                        && (existing.getUpdatedAt() == null || source.getUpdatedAt().isAfter(existing.getUpdatedAt()));
                if (sourceIsNewer) {
                    ensureRecordNotLinkedByOtherReport(targetReportId, targetRecordId);
                    HealthReportDetail toUpdate = new HealthReportDetail();
                    toUpdate.setReportId(targetReportId);
                    toUpdate.setRecordId(targetRecordId);
                    toUpdate.setRevision(existing.getRevision() + 1);
                    toUpdate.setItems(normalizedItems);
                    toUpdate.setUpdatedAt(source.getUpdatedAt());
                    try {
                        healthReportDetailMapper.updateDetailWithRevision(toUpdate, existing.getRevision());
                    } catch (DataIntegrityViolationException exception) {
                        throw new BusinessException("HEALTH_RECORD_ALREADY_LINKED", "该核心指标记录已关联其他体检报告");
                    }
                }
            }
            importedCount++;
        }
        return importedCount;
    }

    private void ensureRecordNotLinkedByOtherReport(Long targetReportId, Long targetRecordId) {
        if (targetRecordId == null) {
            return;
        }
        HealthReportDetail linkedOther = healthReportDetailMapper.findByRecordId(targetRecordId);
        if (linkedOther != null && !targetReportId.equals(linkedOther.getReportId())) {
            throw new BusinessException("HEALTH_RECORD_ALREADY_LINKED", "该核心指标记录已关联其他体检报告");
        }
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

    private HealthVisit findExistingVisit(HealthVisit source, Long targetUserId) {
        HealthVisit byId = source.getId() == null ? null : healthVisitMapper.selectById(source.getId());
        if (byId != null) {
            if (!targetUserId.equals(byId.getOwnerUserId())) {
                throw new BusinessException("DATA_MIGRATION_HEALTH_VISIT_OWNER_CONFLICT", "就诊 ID 与其他用户记录冲突");
            }
            return byId;
        }
        QueryWrapper<HealthVisit> wrapper = new QueryWrapper<>();
        wrapper.eq("owner_user_id", targetUserId).eq("visit_date", source.getVisitDate());
        DataMigrationQuerySupport.eqNullable(wrapper, "hospital_name", source.getHospitalName());
        DataMigrationQuerySupport.eqNullable(wrapper, "department_name", source.getDepartmentName());
        DataMigrationQuerySupport.eqNullable(wrapper, "doctor_name", source.getDoctorName());
        return healthVisitMapper.selectOne(wrapper);
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

    private record RecordNaturalKey(Long ownerUserId, LocalDate measureDate) {
    }

    private record ReportNaturalKey(Long ownerUserId, LocalDate examDate, Long visitId,
                                    String hospitalName, String reportTitle) {
    }
}
