package com.gak.datamigration.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gak.datamigration.DataMigrationConstants;
import com.gak.datamigration.domain.DataMigrationTask;
import com.gak.datamigration.domain.DataMigrationTaskItem;
import com.gak.datamigration.handler.MigrationResourceHandler;
import com.gak.datamigration.mapper.DataMigrationTaskItemMapper;
import com.gak.datamigration.mapper.DataMigrationTaskMapper;
import com.gak.user.domain.user.User;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 迁移任务异步执行器。
 */
@Service
public class DataMigrationTaskExecutionService {

    private final DataMigrationTaskMapper taskMapper;
    private final DataMigrationTaskItemMapper taskItemMapper;
    private final DataMigrationArchiveService archiveService;
    private final DataMigrationPackageStorageService storageService;
    private final DataMigrationAdminGuard adminGuard;
    private final ObjectMapper objectMapper;
    private final Map<String, MigrationResourceHandler> handlerMap;
    private final String applicationName;

    public DataMigrationTaskExecutionService(DataMigrationTaskMapper taskMapper,
                                             DataMigrationTaskItemMapper taskItemMapper,
                                             DataMigrationArchiveService archiveService,
                                             DataMigrationPackageStorageService storageService,
                                             DataMigrationAdminGuard adminGuard,
                                             ObjectMapper objectMapper,
                                             List<MigrationResourceHandler> handlers,
                                             @Value("${spring.application.name:gak-parent}") String applicationName) {
        this.taskMapper = taskMapper;
        this.taskItemMapper = taskItemMapper;
        this.archiveService = archiveService;
        this.storageService = storageService;
        this.adminGuard = adminGuard;
        this.objectMapper = objectMapper;
        this.applicationName = applicationName;
        this.handlerMap = new LinkedHashMap<>();
        handlers.stream()
                .sorted(Comparator.comparingInt(MigrationResourceHandler::order))
                .forEach(handler -> this.handlerMap.put(handler.resourceCode(), handler));
    }

    public void runExportTask(Long taskId) {
        DataMigrationTask task = taskMapper.selectById(taskId);
        if (task == null) {
            return;
        }
        Map<String, DataMigrationTaskItem> itemMap = loadItemMap(taskId);
        List<MigrationResourceHandler> handlers = resolveTaskHandlers(task);
        List<MigrationResourceHandler.MigrationResourceExportData> exports = new ArrayList<>();
        long recordCount = 0L;
        long attachmentCount = 0L;

        try {
            for (MigrationResourceHandler handler : handlers) {
                DataMigrationTaskItem item = itemMap.get(handler.resourceCode());
                markItemRunning(item);
                try {
                    MigrationResourceHandler.MigrationResourceExportData exportData =
                            handler.exportData(new MigrationResourceHandler.ExportContext(Boolean.TRUE.equals(task.getIncludeAttachments())));
                    exports.add(exportData);
                    recordCount += exportData.recordCount();
                    attachmentCount += exportData.attachmentCount();
                    markItemSuccess(item, exportData.recordCount(), exportData.attachmentCount(), "导出资源完成");
                } catch (Exception exception) {
                    markItemFailed(item, exception.getMessage());
                    markTaskFinished(taskId, DataMigrationConstants.TASK_STATUS_FAILED, recordCount, attachmentCount,
                            null, exception.getMessage(), null);
                    return;
                }
            }

            User operator = adminGuard.requireExists(task.getOperatorUserId());
            DataMigrationArchiveService.BuildPackageResult buildResult = archiveService.buildExportPackage(
                    new DataMigrationArchiveService.BuildPackageRequest(
                            task.getPackageName(),
                            applicationName,
                            task.getCreatedAt(),
                            resolveOperatorName(operator),
                            task.getScopeMode(),
                            readCodeList(task.getSystemResourceCodes()),
                            readCodeList(task.getBusinessAppCodes()),
                            recordCount,
                            attachmentCount
                    ),
                    exports
            );
            DataMigrationPackageStorageService.StoredPackageFile storedPackage =
                    storageService.saveExportPackage(task.getPackageName() + ".zip", buildResult.zipFile());

            markTaskFinished(taskId,
                    DataMigrationConstants.TASK_STATUS_SUCCESS,
                    recordCount,
                    attachmentCount,
                    storedPackage,
                    null,
                    null);
        } catch (Exception exception) {
            markTaskFinished(taskId, DataMigrationConstants.TASK_STATUS_FAILED, recordCount, attachmentCount,
                    null, exception.getMessage(), null);
        }
    }

    public void runImportTask(Long taskId) {
        DataMigrationTask task = taskMapper.selectById(taskId);
        if (task == null) {
            return;
        }
        Map<String, DataMigrationTaskItem> itemMap = loadItemMap(taskId);
        long recordCount = 0L;
        long attachmentCount = 0L;
        int successCount = 0;
        int failureCount = 0;
        List<String> errors = new ArrayList<>();

        DataMigrationArchiveService.ExtractedPackage extractedPackage = null;
        try {
            extractedPackage = archiveService.extractPackage(storageService.resolve(task.getFileUrl()));
            MigrationResourceHandler.ImportContext importContext = new MigrationResourceHandler.ImportContext(
                    task.getImportMode(),
                    Boolean.TRUE.equals(task.getIncludeAttachments()),
                    extractedPackage.root()
            );

            for (MigrationResourceHandler handler : resolveTaskHandlers(task)) {
                DataMigrationTaskItem item = itemMap.get(handler.resourceCode());
                markItemRunning(item);
                try {
                    MigrationResourceHandler.MigrationResourceImportResult result = handler.importData(importContext);
                    recordCount += result.recordCount();
                    attachmentCount += result.attachmentCount();
                    markItemSuccess(item, result.recordCount(), result.attachmentCount(),
                            StringUtils.hasText(result.message()) ? result.message() : "导入资源完成");
                    successCount++;
                } catch (Exception exception) {
                    failureCount++;
                    String message = StringUtils.hasText(exception.getMessage()) ? exception.getMessage() : "导入资源失败";
                    errors.add(handler.resourceCode() + ": " + message);
                    markItemFailed(item, message);
                    if (!Boolean.TRUE.equals(task.getContinueOnError())) {
                        markTaskFinished(taskId, DataMigrationConstants.TASK_STATUS_FAILED, recordCount, attachmentCount,
                                null, String.join("; ", errors), null);
                        return;
                    }
                }
            }

            String finalStatus = failureCount == 0
                    ? DataMigrationConstants.TASK_STATUS_SUCCESS
                    : (successCount > 0 ? DataMigrationConstants.TASK_STATUS_PARTIAL_SUCCESS : DataMigrationConstants.TASK_STATUS_FAILED);
            markTaskFinished(taskId, finalStatus, recordCount, attachmentCount, null,
                    errors.isEmpty() ? null : String.join("; ", errors), null);
        } catch (Exception exception) {
            String message = StringUtils.hasText(exception.getMessage()) ? exception.getMessage() : "导入任务执行失败";
            markTaskFinished(taskId, DataMigrationConstants.TASK_STATUS_FAILED, recordCount, attachmentCount, null, message, null);
        } finally {
            if (extractedPackage != null) {
                storageService.deleteQuietly(extractedPackage.root());
            }
        }
    }

    private List<MigrationResourceHandler> resolveTaskHandlers(DataMigrationTask task) {
        List<String> codes = new ArrayList<>();
        codes.addAll(readCodeList(task.getSystemResourceCodes()));
        codes.addAll(readCodeList(task.getBusinessAppCodes()));
        List<MigrationResourceHandler> handlers = new ArrayList<>();
        for (String code : codes) {
            MigrationResourceHandler handler = handlerMap.get(code);
            if (handler != null) {
                handlers.add(handler);
            }
        }
        handlers.sort(Comparator.comparingInt(MigrationResourceHandler::order));
        return handlers;
    }

    private Map<String, DataMigrationTaskItem> loadItemMap(Long taskId) {
        QueryWrapper<DataMigrationTaskItem> wrapper = new QueryWrapper<>();
        wrapper.eq("task_id", taskId).orderByAsc("created_at").orderByAsc("id");
        List<DataMigrationTaskItem> items = taskItemMapper.selectList(wrapper);
        Map<String, DataMigrationTaskItem> result = new LinkedHashMap<>();
        for (DataMigrationTaskItem item : items) {
            result.put(item.getResourceCode(), item);
        }
        return result;
    }

    private void markItemRunning(DataMigrationTaskItem item) {
        if (item == null) {
            return;
        }
        item.setStatus(DataMigrationConstants.TASK_STATUS_RUNNING);
        item.setMessage(null);
        item.setFinishedAt(null);
        taskItemMapper.updateById(item);
    }

    private void markItemSuccess(DataMigrationTaskItem item, long recordCount, long attachmentCount, String message) {
        if (item == null) {
            return;
        }
        item.setStatus(DataMigrationConstants.TASK_STATUS_SUCCESS);
        item.setRecordCount(recordCount);
        item.setAttachmentCount(attachmentCount);
        item.setMessage(message);
        item.setFinishedAt(LocalDateTime.now());
        taskItemMapper.updateById(item);
    }

    private void markItemFailed(DataMigrationTaskItem item, String message) {
        if (item == null) {
            return;
        }
        item.setStatus(DataMigrationConstants.TASK_STATUS_FAILED);
        item.setMessage(message);
        item.setFinishedAt(LocalDateTime.now());
        taskItemMapper.updateById(item);
    }

    private void markTaskFinished(Long taskId,
                                  String status,
                                  long recordCount,
                                  long attachmentCount,
                                  DataMigrationPackageStorageService.StoredPackageFile storedPackage,
                                  String errorMessage,
                                  Long fileSizeOverride) {
        DataMigrationTask updatedTask = new DataMigrationTask();
        updatedTask.setId(taskId);
        updatedTask.setStatus(status);
        updatedTask.setRecordCount(recordCount);
        updatedTask.setAttachmentCount(attachmentCount);
        updatedTask.setErrorMessage(errorMessage);
        updatedTask.setFinishedAt(LocalDateTime.now());
        if (storedPackage != null) {
            updatedTask.setFileStorageType(storedPackage.storageType());
            updatedTask.setFileUrl(storedPackage.storagePath());
            updatedTask.setFileName(storedPackage.fileName());
            updatedTask.setFileSize(fileSizeOverride != null ? fileSizeOverride : storedPackage.fileSize());
        }
        taskMapper.updateById(updatedTask);
    }

    private List<String> readCodeList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception exception) {
            return List.of();
        }
    }

    private String resolveOperatorName(User operator) {
        if (StringUtils.hasText(operator.getUsername())) {
            return operator.getUsername().trim();
        }
        if (StringUtils.hasText(operator.getDisplayName())) {
            return operator.getDisplayName().trim();
        }
        return String.valueOf(operator.getId());
    }
}
