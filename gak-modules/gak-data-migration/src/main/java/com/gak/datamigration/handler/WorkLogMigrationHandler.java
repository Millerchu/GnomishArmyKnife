package com.gak.datamigration.handler;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gak.datamigration.DataMigrationConstants;
import com.gak.datamigration.service.DataMigrationArchiveService;
import com.gak.datamigration.service.DataMigrationBeanMergeSupport;
import com.gak.framework.exception.BusinessException;
import com.gak.user.domain.user.User;
import com.gak.user.mapper.user.UserMapper;
import com.gak.worklog.entity.WorkLog;
import com.gak.worklog.entity.WorkLogItem;
import com.gak.worklog.entity.WorkLogType;
import com.gak.worklog.enums.WorkLogStatus;
import com.gak.worklog.mapper.WorkLogItemMapper;
import com.gak.worklog.mapper.WorkLogMapper;
import com.gak.worklog.mapper.WorkLogTypeMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 工作日志迁移处理器。
 */
@Service
public class WorkLogMigrationHandler implements MigrationResourceHandler {

    private static final String APP_CODE = "APP_WORK_LOG";

    private final WorkLogMapper workLogMapper;
    private final WorkLogItemMapper workLogItemMapper;
    private final WorkLogTypeMapper workLogTypeMapper;
    private final UserMapper userMapper;
    private final DataMigrationArchiveService archiveService;

    public WorkLogMigrationHandler(WorkLogMapper workLogMapper,
                                   WorkLogItemMapper workLogItemMapper,
                                   WorkLogTypeMapper workLogTypeMapper,
                                   UserMapper userMapper,
                                   DataMigrationArchiveService archiveService) {
        this.workLogMapper = workLogMapper;
        this.workLogItemMapper = workLogItemMapper;
        this.workLogTypeMapper = workLogTypeMapper;
        this.userMapper = userMapper;
        this.archiveService = archiveService;
    }

    @Override
    public String resourceCode() {
        return APP_CODE;
    }

    @Override
    public String resourceName() {
        return "工作日志";
    }

    @Override
    public String resourceType() {
        return DataMigrationConstants.RESOURCE_TYPE_BUSINESS;
    }

    @Override
    public boolean attachmentSupported() {
        return false;
    }

    @Override
    public String entryPath() {
        return "business/" + APP_CODE + "/data.json";
    }

    @Override
    public int order() {
        return 110;
    }

    @Override
    public MigrationResourceExportData exportData(ExportContext context) {
        QueryWrapper<WorkLog> logWrapper = new QueryWrapper<>();
        logWrapper.orderByAsc("log_date").orderByAsc("id");
        List<WorkLog> workLogs = workLogMapper.selectList(logWrapper);

        QueryWrapper<WorkLogType> typeWrapper = new QueryWrapper<>();
        typeWrapper.orderByAsc("work_log_id").orderByAsc("type_code").orderByAsc("id");
        List<WorkLogType> workLogTypes = workLogTypeMapper.selectList(typeWrapper);

        QueryWrapper<WorkLogItem> itemWrapper = new QueryWrapper<>();
        itemWrapper.orderByAsc("work_log_id").orderByAsc("sort_no").orderByAsc("id");
        List<WorkLogItem> workLogItems = workLogItemMapper.selectList(itemWrapper);

        long recordCount = (long) workLogs.size() + workLogTypes.size() + workLogItems.size();
        return new MigrationResourceExportData(
                resourceCode(),
                entryPath(),
                new Payload(workLogs, workLogTypes, workLogItems),
                recordCount,
                0L,
                List.of()
        );
    }

    @Override
    @Transactional
    public MigrationResourceImportResult importData(ImportContext context) throws Exception {
        Payload payload = archiveService.readJson(context.packageRoot(), entryPath(), Payload.class);
        Map<Long, List<WorkLogType>> typeMap = buildTypeMap(payload.getWorkLogTypes());
        Map<Long, List<WorkLogItem>> itemMap = buildItemMap(payload.getWorkLogItems());
        long importedCount = 0L;

        for (WorkLog source : payload.getWorkLogs()) {
            if (source == null) {
                continue;
            }
            source.setWorkStatus(normalizeImportedWorkStatus(source.getWorkStatus()));
            Long targetUserId = resolveUserId(source.getUserId(), context);
            if (targetUserId == null) {
                throw new BusinessException("DATA_MIGRATION_WORK_LOG_USER_MISSING", "工作日志依赖的用户不存在: " + source.getUserId());
            }
            WorkLog existing = findByUserDateAndProject(targetUserId, source.getLogDate(), source.getProjectCode());
            Long targetLogId;
            if (existing != null) {
                if (context.isStrict()) {
                    throw new BusinessException("DATA_MIGRATION_WORK_LOG_CONFLICT",
                            "工作日志已存在: userId=" + targetUserId
                                    + ", date=" + source.getLogDate()
                                    + ", project=" + source.getProjectCode());
                }
                source.setUserId(targetUserId);
                if (context.isOverwrite()) {
                    DataMigrationBeanMergeSupport.overwrite(source, existing, "id", "userId");
                } else {
                    DataMigrationBeanMergeSupport.mergeNewestNonNull(source, existing, "id", "userId");
                }
                existing.setUserId(targetUserId);
                workLogMapper.updateById(existing);
                targetLogId = existing.getId();
            } else {
                WorkLog insertLog = copyLog(source);
                insertLog.setUserId(targetUserId);
                if (insertLog.getId() != null && workLogMapper.selectById(insertLog.getId()) != null) {
                    if (context.isStrict()) {
                        throw new BusinessException("DATA_MIGRATION_WORK_LOG_ID_CONFLICT", "工作日志 ID 冲突: " + insertLog.getId());
                    }
                    insertLog.setId(null);
                }
                workLogMapper.insert(insertLog);
                targetLogId = insertLog.getId();
            }
            importedCount++;

            List<WorkLogType> types = typeMap.getOrDefault(source.getId(), List.of());
            for (WorkLogType type : types) {
                WorkLogType existingType = findExistingType(targetLogId, type);
                if (existingType != null) {
                    if (context.isStrict()) {
                        throw new BusinessException("DATA_MIGRATION_WORK_LOG_TYPE_CONFLICT", "工作日志类型已存在: " + type.getTypeCode());
                    }
                    type.setWorkLogId(targetLogId);
                    DataMigrationBeanMergeSupport.mergeNewestNonNull(type, existingType, "id", "workLogId");
                    existingType.setWorkLogId(targetLogId);
                    workLogTypeMapper.updateById(existingType);
                } else {
                    WorkLogType insertType = copyType(type);
                    insertType.setWorkLogId(targetLogId);
                    workLogTypeMapper.insert(insertType);
                }
                importedCount++;
            }

            List<WorkLogItem> workItems = itemMap.getOrDefault(source.getId(), List.of());
            if (workItems.isEmpty()) {
                workItems = buildLegacyWorkItems(source);
            }
            for (WorkLogItem workItem : workItems) {
                workItem.setStatus(normalizeImportedWorkStatus(workItem.getStatus()));
                WorkLogItem existingItem = findExistingItem(targetLogId, workItem);
                if (existingItem != null) {
                    if (context.isStrict()) {
                        throw new BusinessException(
                                "DATA_MIGRATION_WORK_LOG_ITEM_CONFLICT",
                                "工作日志内容条目已存在: sortNo=" + workItem.getSortNo()
                        );
                    }
                    workItem.setWorkLogId(targetLogId);
                    if (context.isOverwrite()) {
                        DataMigrationBeanMergeSupport.overwrite(workItem, existingItem, "id", "workLogId");
                    } else {
                        DataMigrationBeanMergeSupport.mergeNewestNonNull(workItem, existingItem, "id", "workLogId");
                    }
                    existingItem.setWorkLogId(targetLogId);
                    workLogItemMapper.updateById(existingItem);
                } else {
                    WorkLogItem insertItem = copyItem(workItem);
                    insertItem.setWorkLogId(targetLogId);
                    if (insertItem.getId() != null && workLogItemMapper.selectById(insertItem.getId()) != null) {
                        if (context.isStrict()) {
                            throw new BusinessException(
                                    "DATA_MIGRATION_WORK_LOG_ITEM_ID_CONFLICT",
                                    "工作日志内容条目 ID 冲突: " + insertItem.getId()
                            );
                        }
                        insertItem.setId(null);
                    }
                    workLogItemMapper.insert(insertItem);
                }
                importedCount++;
            }
        }

        return MigrationResourceImportResult.success(importedCount, 0L, "工作日志导入完成");
    }

    private Map<Long, List<WorkLogType>> buildTypeMap(List<WorkLogType> workLogTypes) {
        Map<Long, List<WorkLogType>> result = new LinkedHashMap<>();
        if (workLogTypes == null) {
            return result;
        }
        for (WorkLogType workLogType : workLogTypes) {
            result.computeIfAbsent(workLogType.getWorkLogId(), key -> new ArrayList<>()).add(workLogType);
        }
        return result;
    }

    private Map<Long, List<WorkLogItem>> buildItemMap(List<WorkLogItem> workLogItems) {
        Map<Long, List<WorkLogItem>> result = new LinkedHashMap<>();
        if (workLogItems == null) {
            return result;
        }
        for (WorkLogItem workLogItem : workLogItems) {
            result.computeIfAbsent(workLogItem.getWorkLogId(), key -> new ArrayList<>()).add(workLogItem);
        }
        return result;
    }

    private List<WorkLogItem> buildLegacyWorkItems(WorkLog source) {
        List<WorkLogItem> result = new ArrayList<>();
        if (source.getContent() == null) {
            return result;
        }
        int sortNo = 1;
        for (String line : source.getContent().split("\\r?\\n")) {
            if (line.isBlank()) {
                continue;
            }
            WorkLogItem workLogItem = new WorkLogItem();
            workLogItem.setWorkLogId(source.getId());
            workLogItem.setContent(line.trim());
            workLogItem.setStatus(source.getWorkStatus());
            workLogItem.setSortNo(sortNo++);
            workLogItem.setCreatedAt(source.getCreatedAt());
            workLogItem.setUpdatedAt(source.getUpdatedAt());
            result.add(workLogItem);
        }
        return result;
    }

    private WorkLog findByUserDateAndProject(Long userId,
                                             java.time.LocalDate logDate,
                                             String projectCode) {
        QueryWrapper<WorkLog> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId)
                .eq("log_date", logDate)
                .eq(projectCode != null, "project_code", projectCode)
                .isNull(projectCode == null, "project_code");
        return workLogMapper.selectOne(wrapper);
    }

    private WorkLogType findExistingType(Long workLogId, WorkLogType sourceType) {
        WorkLogType byId = sourceType.getId() == null ? null : workLogTypeMapper.selectById(sourceType.getId());
        if (byId != null) {
            return byId;
        }
        QueryWrapper<WorkLogType> wrapper = new QueryWrapper<>();
        wrapper.eq("work_log_id", workLogId).eq("type_code", sourceType.getTypeCode());
        return workLogTypeMapper.selectOne(wrapper);
    }

    private WorkLogItem findExistingItem(Long workLogId, WorkLogItem sourceItem) {
        WorkLogItem byId = sourceItem.getId() == null ? null : workLogItemMapper.selectById(sourceItem.getId());
        if (byId != null) {
            return byId;
        }
        QueryWrapper<WorkLogItem> wrapper = new QueryWrapper<>();
        wrapper.eq("work_log_id", workLogId).eq("sort_no", sourceItem.getSortNo());
        return workLogItemMapper.selectOne(wrapper);
    }

    private Long resolveUserId(Long sourceUserId, ImportContext context) {
        Long mappedId = context.mappedUserId(sourceUserId);
        if (mappedId != null) {
            return mappedId;
        }
        User sameUser = sourceUserId == null ? null : userMapper.selectById(sourceUserId);
        return sameUser == null ? null : sameUser.getId();
    }

    private String normalizeImportedWorkStatus(String workStatus) {
        if (workStatus == null || workStatus.isBlank()) {
            return WorkLogStatus.COMPLETED.name();
        }
        try {
            return WorkLogStatus.fromCode(workStatus).name();
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    "DATA_MIGRATION_WORK_LOG_STATUS_INVALID",
                    "工作日志状态非法: " + workStatus
            );
        }
    }

    private WorkLog copyLog(WorkLog source) {
        WorkLog workLog = new WorkLog();
        DataMigrationBeanMergeSupport.overwrite(source, workLog);
        return workLog;
    }

    private WorkLogType copyType(WorkLogType source) {
        WorkLogType type = new WorkLogType();
        DataMigrationBeanMergeSupport.overwrite(source, type);
        return type;
    }

    private WorkLogItem copyItem(WorkLogItem source) {
        WorkLogItem workLogItem = new WorkLogItem();
        DataMigrationBeanMergeSupport.overwrite(source, workLogItem);
        return workLogItem;
    }

    /**
     * 工作日志导出载荷。
     */
    public static class Payload {

        private List<WorkLog> workLogs;
        private List<WorkLogType> workLogTypes;
        private List<WorkLogItem> workLogItems;

        public Payload() {
        }

        public Payload(List<WorkLog> workLogs,
                       List<WorkLogType> workLogTypes,
                       List<WorkLogItem> workLogItems) {
            this.workLogs = workLogs;
            this.workLogTypes = workLogTypes;
            this.workLogItems = workLogItems;
        }

        public List<WorkLog> getWorkLogs() {
            return workLogs;
        }

        public void setWorkLogs(List<WorkLog> workLogs) {
            this.workLogs = workLogs;
        }

        public List<WorkLogType> getWorkLogTypes() {
            return workLogTypes;
        }

        public void setWorkLogTypes(List<WorkLogType> workLogTypes) {
            this.workLogTypes = workLogTypes;
        }

        public List<WorkLogItem> getWorkLogItems() {
            return workLogItems;
        }

        public void setWorkLogItems(List<WorkLogItem> workLogItems) {
            this.workLogItems = workLogItems;
        }
    }
}
