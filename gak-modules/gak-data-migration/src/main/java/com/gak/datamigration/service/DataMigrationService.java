package com.gak.datamigration.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gak.datamigration.DataMigrationConstants;
import com.gak.datamigration.domain.DataMigrationTask;
import com.gak.datamigration.domain.DataMigrationTaskItem;
import com.gak.datamigration.dto.CreateDataMigrationExportRequest;
import com.gak.datamigration.dto.CreateDataMigrationImportMetadata;
import com.gak.datamigration.dto.DataMigrationTaskQueryRequest;
import com.gak.datamigration.handler.MigrationResourceHandler;
import com.gak.datamigration.mapper.DataMigrationTaskItemMapper;
import com.gak.datamigration.mapper.DataMigrationTaskMapper;
import com.gak.datamigration.vo.DataMigrationResourcesVO;
import com.gak.datamigration.vo.DataMigrationTaskVO;
import com.gak.datamigration.vo.DeleteDataMigrationTaskVO;
import com.gak.framework.exception.BusinessException;
import com.gak.framework.response.PagedResult;
import com.gak.permission.domain.SystemApp;
import com.gak.permission.mapper.SystemAppMapper;
import com.gak.user.domain.user.User;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * 数据迁移服务。
 */
@Service
public class DataMigrationService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DataMigrationTaskMapper taskMapper;
    private final DataMigrationTaskItemMapper taskItemMapper;
    private final SystemAppMapper systemAppMapper;
    private final DataMigrationAdminGuard adminGuard;
    private final DataMigrationPackageStorageService storageService;
    private final DataMigrationArchiveService archiveService;
    private final DataMigrationTaskExecutionService taskExecutionService;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final TaskExecutor taskExecutor;
    private final Map<String, MigrationResourceHandler> handlerMap;
    private final String publicUrlPrefix;

    public DataMigrationService(DataMigrationTaskMapper taskMapper,
                                DataMigrationTaskItemMapper taskItemMapper,
                                SystemAppMapper systemAppMapper,
                                DataMigrationAdminGuard adminGuard,
                                DataMigrationPackageStorageService storageService,
                                DataMigrationArchiveService archiveService,
                                DataMigrationTaskExecutionService taskExecutionService,
                                ObjectMapper objectMapper,
                                Validator validator,
                                TaskExecutor taskExecutor,
                                List<MigrationResourceHandler> handlers,
                                @Value("${gak.data-migration.public-url-prefix:/api/system/data-migrations/tasks/}") String publicUrlPrefix) {
        this.taskMapper = taskMapper;
        this.taskItemMapper = taskItemMapper;
        this.systemAppMapper = systemAppMapper;
        this.adminGuard = adminGuard;
        this.storageService = storageService;
        this.archiveService = archiveService;
        this.taskExecutionService = taskExecutionService;
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.taskExecutor = taskExecutor;
        this.publicUrlPrefix = publicUrlPrefix.endsWith("/") ? publicUrlPrefix : publicUrlPrefix + "/";
        this.handlerMap = new LinkedHashMap<>();
        handlers.stream()
                .sorted(Comparator.comparingInt(MigrationResourceHandler::order))
                .forEach(handler -> this.handlerMap.put(handler.resourceCode(), handler));
    }

    public DataMigrationResourcesVO resources(Long currentUserId) {
        adminGuard.requireAdmin(currentUserId);
        DataMigrationResourcesVO result = new DataMigrationResourcesVO();
        result.setSystemResources(listSystemResources());
        result.setBusinessApps(listBusinessApps());
        return result;
    }

    public PagedResult<DataMigrationTaskVO> page(Long currentUserId, DataMigrationTaskQueryRequest request) {
        adminGuard.requireAdmin(currentUserId);
        QueryWrapper<DataMigrationTask> wrapper = new QueryWrapper<>();
        String normalizedTaskType = normalizeTaskType(request.getTaskType(), false);
        String normalizedStatus = normalizeTaskStatus(request.getStatus(), false);
        if (normalizedTaskType != null) {
            wrapper.eq("task_type", normalizedTaskType);
        }
        if (normalizedStatus != null) {
            wrapper.eq("status", normalizedStatus);
        }
        wrapper.orderByDesc("created_at").orderByDesc("id");

        List<DataMigrationTask> tasks = taskMapper.selectList(wrapper);
        long total = tasks.size();
        long fromIndex = Math.max((request.getPageNo() - 1) * request.getPageSize(), 0L);
        long toIndex = Math.min(fromIndex + request.getPageSize(), total);
        if (fromIndex >= total) {
            return new PagedResult<>(List.of(), total);
        }
        List<DataMigrationTaskVO> list = new ArrayList<>();
        for (DataMigrationTask task : tasks.subList((int) fromIndex, (int) toIndex)) {
            list.add(toTaskVO(task, false));
        }
        return new PagedResult<>(list, total);
    }

    public DataMigrationTaskVO detail(Long currentUserId, Long taskId) {
        adminGuard.requireAdmin(currentUserId);
        DataMigrationTask task = getTaskOrThrow(taskId);
        return toTaskVO(task, true);
    }

    @Transactional
    public DataMigrationTaskVO createExportTask(Long currentUserId, CreateDataMigrationExportRequest request) {
        User operator = adminGuard.requireAdmin(currentUserId);
        ResolvedSelection selection = resolveExportSelection(request);

        LocalDateTime now = LocalDateTime.now();
        DataMigrationTask task = new DataMigrationTask();
        task.setTaskNo(generateTaskNo());
        task.setTaskType(DataMigrationConstants.TASK_TYPE_EXPORT);
        task.setStatus(DataMigrationConstants.TASK_STATUS_RUNNING);
        task.setScopeMode(selection.scopeMode());
        task.setPackageName(normalizePackageName(request.getPackageName()));
        task.setSystemResourceCodes(writeCodeList(selection.systemCodes()));
        task.setBusinessAppCodes(writeCodeList(selection.businessCodes()));
        task.setIncludeAttachments(Boolean.TRUE.equals(request.getIncludeAttachments()));
        task.setContinueOnError(false);
        task.setRecordCount(0L);
        task.setAttachmentCount(0L);
        task.setFileName(task.getPackageName() + ".zip");
        task.setRemark(trimToNull(request.getRemark()));
        task.setOperatorUserId(operator.getId());
        task.setCreatedAt(now);
        taskMapper.insert(task);
        createTaskItems(task.getId(), selection.handlers(), now);

        executeAfterCommit(() -> taskExecutionService.runExportTask(task.getId()));
        return toTaskVO(task, true);
    }

    @Transactional
    public DataMigrationTaskVO createImportTask(Long currentUserId, MultipartFile file, String metadataJson) {
        User operator = adminGuard.requireAdmin(currentUserId);
        CreateDataMigrationImportMetadata metadata = parseAndValidateMetadata(metadataJson);

        DataMigrationPackageStorageService.StoredPackageFile storedFile = null;
        try {
            storedFile = storageService.saveImportPackage(file);
            DataMigrationArchiveService.ValidatedImportPackage validatedPackage =
                    archiveService.validateImportPackage(storedFile.localPath());
            List<String> systemCodes = normalizeRequestedCodes(validatedPackage.manifest().systemResourceCodes());
            List<String> businessCodes = normalizeRequestedCodes(validatedPackage.manifest().businessAppCodes());
            List<MigrationResourceHandler> handlers = resolveHandlers(systemCodes, businessCodes, false);
            if (handlers.isEmpty()) {
                throw new BusinessException("DATA_MIGRATION_EMPTY_PACKAGE", "迁移包中没有可执行的资源");
            }

            LocalDateTime now = LocalDateTime.now();
            DataMigrationTask task = new DataMigrationTask();
            task.setTaskNo(generateTaskNo());
            task.setTaskType(DataMigrationConstants.TASK_TYPE_IMPORT);
            task.setStatus(DataMigrationConstants.TASK_STATUS_RUNNING);
            task.setScopeMode(normalizeScopeMode(validatedPackage.manifest().scopeMode(), false));
            task.setPackageName(validatedPackage.manifest().packageName());
            task.setSystemResourceCodes(writeCodeList(systemCodes));
            task.setBusinessAppCodes(writeCodeList(businessCodes));
            task.setIncludeAttachments(Boolean.TRUE.equals(metadata.getIncludeAttachments()));
            task.setImportMode(normalizeImportMode(metadata.getImportMode(), true));
            task.setContinueOnError(Boolean.TRUE.equals(metadata.getContinueOnError()));
            task.setRecordCount(0L);
            task.setAttachmentCount(0L);
            task.setFileUrl(storedFile.storagePath());
            task.setFileStorageType(storedFile.storageType());
            task.setFileName(storedFile.fileName());
            task.setFileSize(storedFile.fileSize());
            task.setRemark(trimToNull(metadata.getRemark()));
            task.setOperatorUserId(operator.getId());
            task.setCreatedAt(now);
            taskMapper.insert(task);
            createTaskItems(task.getId(), handlers, now);

            executeAfterCommit(() -> taskExecutionService.runImportTask(task.getId()));
            return toTaskVO(task, true);
        } catch (IOException exception) {
            if (storedFile != null) {
                storageService.deleteQuietly(storedFile.localPath());
            }
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "导入文件保存失败");
        } catch (RuntimeException exception) {
            if (storedFile != null) {
                storageService.deleteQuietly(storedFile.localPath());
            }
            throw exception;
        }
    }

    private void executeAfterCommit(Runnable task) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            taskExecutor.execute(task);
            return;
        }
        // 异步任务依赖任务主表和明细，必须等事务提交后再读取。
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                taskExecutor.execute(task);
            }
        });
    }

    public DownloadFile download(Long currentUserId, Long taskId) {
        adminGuard.requireAdmin(currentUserId);
        DataMigrationTask task = getTaskOrThrow(taskId);
        if (!DataMigrationConstants.TASK_TYPE_EXPORT.equals(task.getTaskType())) {
            throw new BusinessException("DATA_MIGRATION_DOWNLOAD_UNSUPPORTED", "仅导出任务支持下载");
        }
        if (!DataMigrationConstants.TASK_STATUS_SUCCESS.equals(task.getStatus())) {
            throw new BusinessException("DATA_MIGRATION_DOWNLOAD_NOT_READY", "导出任务尚未完成");
        }
        if (!StringUtils.hasText(task.getFileUrl())) {
            throw new BusinessException("DATA_MIGRATION_FILE_MISSING", "导出文件不存在");
        }
        try {
            Resource resource = storageService.loadAsResource(task.getFileUrl());
            if (!resource.exists()) {
                throw new BusinessException("DATA_MIGRATION_FILE_MISSING", "导出文件不存在");
            }
            return new DownloadFile(resource, task.getFileName(), MediaType.APPLICATION_OCTET_STREAM, task.getFileSize());
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "导出文件读取失败");
        }
    }

    @Transactional
    public DeleteDataMigrationTaskVO deleteExportTask(Long currentUserId, Long taskId) {
        adminGuard.requireAdmin(currentUserId);
        DataMigrationTask task = getTaskOrThrow(taskId);
        validateDeleteAllowed(task);

        cleanupExportArtifacts(task);

        QueryWrapper<DataMigrationTaskItem> itemWrapper = new QueryWrapper<>();
        itemWrapper.eq("task_id", taskId);
        taskItemMapper.delete(itemWrapper);
        taskMapper.deleteById(taskId);

        DeleteDataMigrationTaskVO result = new DeleteDataMigrationTaskVO();
        result.setId(taskId);
        result.setDeleted(true);
        return result;
    }

    private List<DataMigrationResourcesVO.SystemResourceVO> listSystemResources() {
        List<DataMigrationResourcesVO.SystemResourceVO> resources = new ArrayList<>();
        handlerMap.values().stream()
                .filter(handler -> DataMigrationConstants.RESOURCE_TYPE_SYSTEM.equals(handler.resourceType()))
                .sorted(Comparator.comparingInt(MigrationResourceHandler::order))
                .forEach(handler -> {
                    DataMigrationResourcesVO.SystemResourceVO vo = new DataMigrationResourcesVO.SystemResourceVO();
                    vo.setCode(handler.resourceCode());
                    vo.setName(handler.resourceName());
                    vo.setDescription(resolveSystemDescription(handler.resourceCode()));
                    vo.setAttachmentSupported(handler.attachmentSupported());
                    resources.add(vo);
                });
        return resources;
    }

    private List<DataMigrationResourcesVO.BusinessAppVO> listBusinessApps() {
        QueryWrapper<SystemApp> wrapper = new QueryWrapper<>();
        wrapper.orderByAsc("sort_no").orderByAsc("id");
        List<SystemApp> apps = systemAppMapper.selectList(wrapper);
        List<DataMigrationResourcesVO.BusinessAppVO> result = new ArrayList<>();
        for (SystemApp app : apps) {
            MigrationResourceHandler handler = handlerMap.get(app.getAppCode());
            if (handler == null || !DataMigrationConstants.RESOURCE_TYPE_BUSINESS.equals(handler.resourceType())) {
                continue;
            }
            if (StringUtils.hasText(app.getRoutePath()) && app.getRoutePath().startsWith("/system")) {
                continue;
            }
            DataMigrationResourcesVO.BusinessAppVO vo = new DataMigrationResourcesVO.BusinessAppVO();
            vo.setId(app.getId());
            vo.setAppCode(app.getAppCode());
            vo.setFeatureCode(app.getAppCode());
            vo.setCode(app.getAppCode());
            vo.setName(app.getAppName());
            vo.setRoute(app.getRoutePath());
            vo.setCategory(app.getCategory());
            vo.setSecurityLevel(app.getSecurityLevel());
            vo.setEnabled(Boolean.TRUE.equals(app.getEnabled()));
            vo.setDescription(app.getDescription());
            result.add(vo);
        }
        return result;
    }

    private ResolvedSelection resolveExportSelection(CreateDataMigrationExportRequest request) {
        String scopeMode = normalizeScopeMode(request.getScopeMode(), true);
        List<String> allSystemCodes = listSystemResources().stream().map(DataMigrationResourcesVO.SystemResourceVO::getCode).toList();
        List<String> allBusinessCodes = listBusinessApps().stream().map(DataMigrationResourcesVO.BusinessAppVO::getAppCode).toList();

        List<String> systemCodes;
        List<String> businessCodes;
        if (DataMigrationConstants.SCOPE_MODE_ALL.equals(scopeMode)) {
            systemCodes = allSystemCodes;
            businessCodes = allBusinessCodes;
        } else if (DataMigrationConstants.SCOPE_MODE_SYSTEM_ONLY.equals(scopeMode)) {
            systemCodes = allSystemCodes;
            businessCodes = List.of();
        } else if (DataMigrationConstants.SCOPE_MODE_BUSINESS_ONLY.equals(scopeMode)) {
            systemCodes = List.of();
            businessCodes = allBusinessCodes;
        } else {
            systemCodes = normalizeRequestedCodes(request.getSystemResourceCodes());
            businessCodes = normalizeRequestedCodes(request.getBusinessAppCodes());
        }
        List<MigrationResourceHandler> handlers = resolveHandlers(systemCodes, businessCodes, true);
        if (handlers.isEmpty()) {
            throw new BusinessException("DATA_MIGRATION_EMPTY_SELECTION", "至少选择一项迁移资源");
        }
        return new ResolvedSelection(scopeMode, systemCodes, businessCodes, handlers);
    }

    private List<MigrationResourceHandler> resolveHandlers(List<String> systemCodes, List<String> businessCodes, boolean validateBusinessCatalog) {
        Set<String> availableBusinessCodes = validateBusinessCatalog
                ? listBusinessApps().stream().map(DataMigrationResourcesVO.BusinessAppVO::getAppCode).collect(Collectors.toCollection(LinkedHashSet::new))
                : null;
        List<MigrationResourceHandler> handlers = new ArrayList<>();
        for (String code : systemCodes) {
            MigrationResourceHandler handler = handlerMap.get(code);
            if (handler == null || !DataMigrationConstants.RESOURCE_TYPE_SYSTEM.equals(handler.resourceType())) {
                throw new BusinessException("DATA_MIGRATION_SYSTEM_RESOURCE_INVALID", "系统资源不存在: " + code);
            }
            handlers.add(handler);
        }
        for (String code : businessCodes) {
            MigrationResourceHandler handler = handlerMap.get(code);
            if (handler == null || !DataMigrationConstants.RESOURCE_TYPE_BUSINESS.equals(handler.resourceType())) {
                throw new BusinessException("DATA_MIGRATION_BUSINESS_RESOURCE_INVALID", "业务资源不存在: " + code);
            }
            if (validateBusinessCatalog && (availableBusinessCodes == null || !availableBusinessCodes.contains(code))) {
                throw new BusinessException("DATA_MIGRATION_BUSINESS_RESOURCE_INVALID", "业务应用未在应用目录中启用: " + code);
            }
            handlers.add(handler);
        }
        handlers.sort(Comparator.comparingInt(MigrationResourceHandler::order));
        return handlers;
    }

    private void createTaskItems(Long taskId, List<MigrationResourceHandler> handlers, LocalDateTime createdAt) {
        for (MigrationResourceHandler handler : handlers) {
            DataMigrationTaskItem item = new DataMigrationTaskItem();
            item.setTaskId(taskId);
            item.setResourceCode(handler.resourceCode());
            item.setResourceName(handler.resourceName());
            item.setResourceType(handler.resourceType());
            item.setStatus(DataMigrationConstants.TASK_STATUS_PENDING);
            item.setRecordCount(0L);
            item.setAttachmentCount(0L);
            item.setCreatedAt(createdAt);
            taskItemMapper.insert(item);
        }
    }

    private DataMigrationTask getTaskOrThrow(Long taskId) {
        DataMigrationTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "迁移任务不存在");
        }
        return task;
    }

    private DataMigrationTaskVO toTaskVO(DataMigrationTask task, boolean includeItems) {
        DataMigrationTaskVO vo = new DataMigrationTaskVO();
        vo.setId(task.getId());
        vo.setTaskNo(task.getTaskNo());
        vo.setTaskType(task.getTaskType());
        vo.setStatus(task.getStatus());
        vo.setPackageName(task.getPackageName());
        vo.setFileName(task.getFileName());
        vo.setFileSize(task.getFileSize());
        vo.setSystemResourceCount(readCodeList(task.getSystemResourceCodes()).size());
        vo.setBusinessAppCount(readCodeList(task.getBusinessAppCodes()).size());
        vo.setRecordCount(task.getRecordCount() != null ? task.getRecordCount() : 0L);
        vo.setAttachmentCount(task.getAttachmentCount() != null ? task.getAttachmentCount() : 0L);
        vo.setCanDownload(canDownload(task));
        vo.setCanDelete(canDelete(task));
        vo.setDownloadUrl(canDownload(task) ? publicUrlPrefix + task.getId() + "/download" : null);
        vo.setMessage(resolveTaskMessage(task));
        vo.setRemark(task.getRemark());
        vo.setCreatedAt(formatDateTime(task.getCreatedAt()));
        vo.setFinishedAt(formatDateTime(task.getFinishedAt()));
        if (includeItems) {
            vo.setItems(listTaskItems(task.getId()));
        }
        return vo;
    }

    private List<DataMigrationTaskVO.ItemVO> listTaskItems(Long taskId) {
        QueryWrapper<DataMigrationTaskItem> wrapper = new QueryWrapper<>();
        wrapper.eq("task_id", taskId).orderByAsc("created_at").orderByAsc("id");
        List<DataMigrationTaskItem> items = taskItemMapper.selectList(wrapper);
        List<DataMigrationTaskVO.ItemVO> result = new ArrayList<>();
        for (DataMigrationTaskItem item : items) {
            DataMigrationTaskVO.ItemVO vo = new DataMigrationTaskVO.ItemVO();
            vo.setResourceCode(item.getResourceCode());
            vo.setResourceName(item.getResourceName());
            vo.setResourceType(item.getResourceType());
            vo.setStatus(item.getStatus());
            vo.setRecordCount(item.getRecordCount());
            vo.setAttachmentCount(item.getAttachmentCount());
            vo.setMessage(item.getMessage());
            vo.setCreatedAt(formatDateTime(item.getCreatedAt()));
            vo.setFinishedAt(formatDateTime(item.getFinishedAt()));
            result.add(vo);
        }
        return result;
    }

    private boolean canDownload(DataMigrationTask task) {
        return DataMigrationConstants.TASK_TYPE_EXPORT.equals(task.getTaskType())
                && DataMigrationConstants.TASK_STATUS_SUCCESS.equals(task.getStatus())
                && StringUtils.hasText(task.getFileUrl());
    }

    private boolean canDelete(DataMigrationTask task) {
        return DataMigrationConstants.TASK_TYPE_EXPORT.equals(task.getTaskType())
                && !Set.of(DataMigrationConstants.TASK_STATUS_PENDING, DataMigrationConstants.TASK_STATUS_RUNNING)
                .contains(task.getStatus());
    }

    private void validateDeleteAllowed(DataMigrationTask task) {
        if (!DataMigrationConstants.TASK_TYPE_EXPORT.equals(task.getTaskType())) {
            throw new BusinessException("DATA_MIGRATION_DELETE_UNSUPPORTED", "仅导出任务支持删除");
        }
        if (Set.of(DataMigrationConstants.TASK_STATUS_PENDING, DataMigrationConstants.TASK_STATUS_RUNNING)
                .contains(task.getStatus())) {
            throw new BusinessException("DATA_MIGRATION_DELETE_RUNNING_FORBIDDEN", "运行中的导出任务不能删除");
        }
    }

    private void cleanupExportArtifacts(DataMigrationTask task) {
        if (!StringUtils.hasText(task.getFileUrl())) {
            return;
        }
        java.nio.file.Path packagePath = storageService.resolve(task.getFileUrl());
        storageService.deleteQuietly(packagePath);

        String fileName = packagePath.getFileName() != null ? packagePath.getFileName().toString() : null;
        if (StringUtils.hasText(fileName) && fileName.endsWith(".zip")) {
            String baseName = fileName.substring(0, fileName.length() - 4);
            storageService.deleteQuietly(packagePath.getParent().resolve(baseName));
            storageService.deleteQuietly(packagePath.getParent().resolve(baseName + ".tmp"));
        }
    }

    private CreateDataMigrationImportMetadata parseAndValidateMetadata(String metadataJson) {
        try {
            CreateDataMigrationImportMetadata metadata = objectMapper.readValue(metadataJson, CreateDataMigrationImportMetadata.class);
            Set<ConstraintViolation<CreateDataMigrationImportMetadata>> violations = validator.validate(metadata);
            if (!violations.isEmpty()) {
                ConstraintViolation<CreateDataMigrationImportMetadata> violation = violations.iterator().next();
                throw new BusinessException("DATA_MIGRATION_IMPORT_METADATA_INVALID", violation.getMessage());
            }
            metadata.setImportMode(normalizeImportMode(metadata.getImportMode(), true));
            return metadata;
        } catch (JsonProcessingException exception) {
            throw new BusinessException("DATA_MIGRATION_IMPORT_METADATA_INVALID", "metadata 不是合法的 JSON");
        }
    }

    private String normalizeTaskType(String taskType, boolean required) {
        String normalized = normalizeUpper(taskType);
        if (!StringUtils.hasText(normalized)) {
            return required ? throwBusiness("DATA_MIGRATION_TASK_TYPE_REQUIRED", "taskType 不能为空") : null;
        }
        if (!Set.of(DataMigrationConstants.TASK_TYPE_EXPORT, DataMigrationConstants.TASK_TYPE_IMPORT).contains(normalized)) {
            throw new BusinessException("DATA_MIGRATION_TASK_TYPE_INVALID", "taskType 非法");
        }
        return normalized;
    }

    private String normalizeTaskStatus(String status, boolean required) {
        String normalized = normalizeUpper(status);
        if (!StringUtils.hasText(normalized)) {
            return required ? throwBusiness("DATA_MIGRATION_STATUS_REQUIRED", "status 不能为空") : null;
        }
        if (!Set.of(
                DataMigrationConstants.TASK_STATUS_PENDING,
                DataMigrationConstants.TASK_STATUS_RUNNING,
                DataMigrationConstants.TASK_STATUS_SUCCESS,
                DataMigrationConstants.TASK_STATUS_FAILED,
                DataMigrationConstants.TASK_STATUS_PARTIAL_SUCCESS
        ).contains(normalized)) {
            throw new BusinessException("DATA_MIGRATION_STATUS_INVALID", "status 非法");
        }
        return normalized;
    }

    private String normalizeScopeMode(String scopeMode, boolean required) {
        String normalized = normalizeUpper(scopeMode);
        if (!StringUtils.hasText(normalized)) {
            return required ? throwBusiness("DATA_MIGRATION_SCOPE_REQUIRED", "scopeMode 不能为空") : null;
        }
        if (!Set.of(
                DataMigrationConstants.SCOPE_MODE_ALL,
                DataMigrationConstants.SCOPE_MODE_SYSTEM_ONLY,
                DataMigrationConstants.SCOPE_MODE_BUSINESS_ONLY,
                DataMigrationConstants.SCOPE_MODE_CUSTOM
        ).contains(normalized)) {
            throw new BusinessException("DATA_MIGRATION_SCOPE_INVALID", "scopeMode 非法");
        }
        return normalized;
    }

    private String normalizeImportMode(String importMode, boolean required) {
        String normalized = normalizeUpper(importMode);
        if (!StringUtils.hasText(normalized)) {
            return required ? throwBusiness("DATA_MIGRATION_IMPORT_MODE_REQUIRED", "importMode 不能为空") : null;
        }
        if (!Set.of(
                DataMigrationConstants.IMPORT_MODE_MERGE,
                DataMigrationConstants.IMPORT_MODE_OVERWRITE,
                DataMigrationConstants.IMPORT_MODE_STRICT
        ).contains(normalized)) {
            throw new BusinessException("DATA_MIGRATION_IMPORT_MODE_INVALID", "importMode 非法");
        }
        return normalized;
    }

    private List<String> normalizeRequestedCodes(List<String> requestedCodes) {
        if (requestedCodes == null || requestedCodes.isEmpty()) {
            return List.of();
        }
        Set<String> normalizedCodes = new LinkedHashSet<>();
        for (String requestedCode : requestedCodes) {
            String normalized = normalizeUpper(requestedCode);
            if (StringUtils.hasText(normalized)) {
                normalizedCodes.add(normalized);
            }
        }
        return new ArrayList<>(normalizedCodes);
    }

    private String writeCodeList(List<String> codes) {
        try {
            return objectMapper.writeValueAsString(codes == null ? List.of() : codes);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("资源编码序列化失败", exception);
        }
    }

    private List<String> readCodeList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (IOException exception) {
            return List.of();
        }
    }

    private String normalizePackageName(String packageName) {
        String normalized = trimToNull(packageName);
        if (!StringUtils.hasText(normalized)) {
            throw new BusinessException("DATA_MIGRATION_PACKAGE_NAME_REQUIRED", "packageName 不能为空");
        }
        return normalized.replaceAll("[\\\\/:*?\"<>|]", "-");
    }

    private String resolveTaskMessage(DataMigrationTask task) {
        if (StringUtils.hasText(task.getErrorMessage())) {
            return task.getErrorMessage();
        }
        return switch (task.getStatus()) {
            case DataMigrationConstants.TASK_STATUS_SUCCESS ->
                    DataMigrationConstants.TASK_TYPE_EXPORT.equals(task.getTaskType()) ? "导出完成" : "导入完成";
            case DataMigrationConstants.TASK_STATUS_RUNNING ->
                    DataMigrationConstants.TASK_TYPE_EXPORT.equals(task.getTaskType()) ? "导出任务执行中" : "导入任务执行中";
            case DataMigrationConstants.TASK_STATUS_PARTIAL_SUCCESS -> "部分资源执行成功";
            case DataMigrationConstants.TASK_STATUS_FAILED -> "任务执行失败";
            default -> "任务待执行";
        };
    }

    private String resolveSystemDescription(String code) {
        return switch (code) {
            case DataMigrationConstants.SYSTEM_RESOURCE_USERS -> "系统用户、角色标识、状态和个人资料等基础账号数据。";
            case DataMigrationConstants.SYSTEM_RESOURCE_APPS -> "应用管理维护的应用元数据、路由、图标和上下线配置。";
            case DataMigrationConstants.SYSTEM_RESOURCE_PERMISSIONS -> "用户与应用之间的授权关系和授予信息。";
            case DataMigrationConstants.SYSTEM_RESOURCE_DICTIONARIES -> "数据字典主表、字典项和业务字段绑定配置。";
            default -> "系统资源数据。";
        };
    }

    private String generateTaskNo() {
        return "DM" + java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")
                .format(LocalDateTime.now());
    }

    private String normalizeUpper(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim() : null;
        return StringUtils.hasText(normalized) ? normalized : null;
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? null : DATE_TIME_FORMATTER.format(value);
    }

    private String throwBusiness(String code, String message) {
        throw new BusinessException(code, message);
    }

    /**
     * 下载文件。
     */
    public record DownloadFile(Resource resource, String fileName, MediaType mediaType, Long fileSize) {
    }

    private record ResolvedSelection(String scopeMode,
                                     List<String> systemCodes,
                                     List<String> businessCodes,
                                     List<MigrationResourceHandler> handlers) {
    }
}
