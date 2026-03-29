package com.gak.datamigration.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gak.datamigration.DataMigrationConstants;
import com.gak.datamigration.domain.DataMigrationTask;
import com.gak.datamigration.dto.CreateDataMigrationExportRequest;
import com.gak.datamigration.handler.MigrationResourceHandler;
import com.gak.datamigration.mapper.DataMigrationTaskItemMapper;
import com.gak.datamigration.mapper.DataMigrationTaskMapper;
import com.gak.framework.exception.BusinessException;
import com.gak.permission.mapper.SystemAppMapper;
import com.gak.user.domain.user.User;
import jakarta.validation.Validation;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataMigrationServiceTest {

    @Mock
    private DataMigrationTaskMapper taskMapper;

    @Mock
    private DataMigrationTaskItemMapper taskItemMapper;

    @Mock
    private SystemAppMapper systemAppMapper;

    @Mock
    private DataMigrationAdminGuard adminGuard;

    @Mock
    private DataMigrationPackageStorageService storageService;

    @Mock
    private DataMigrationArchiveService archiveService;

    @Mock
    private DataMigrationTaskExecutionService taskExecutionService;

    @Test
    void createExportTaskShouldRejectEmptySelection() {
        User admin = new User();
        admin.setId(1L);
        admin.setRoleCode("ADMIN");
        when(adminGuard.requireAdmin(1L)).thenReturn(admin);

        DataMigrationService service = new DataMigrationService(
                taskMapper,
                taskItemMapper,
                systemAppMapper,
                adminGuard,
                storageService,
                archiveService,
                taskExecutionService,
                new ObjectMapper().findAndRegisterModules(),
                Validation.buildDefaultValidatorFactory().getValidator(),
                new NoopTaskExecutor(),
                List.of(new StubHandler()),
                "/api/system/data-migrations/tasks/"
        );

        CreateDataMigrationExportRequest request = new CreateDataMigrationExportRequest();
        request.setScopeMode(DataMigrationConstants.SCOPE_MODE_CUSTOM);
        request.setPackageName("empty-package");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createExportTask(1L, request));
        assertEquals("DATA_MIGRATION_EMPTY_SELECTION", exception.getCode());
    }

    @Test
    void deleteExportTaskShouldDeleteTaskAndPackage() {
        User admin = new User();
        admin.setId(1L);
        admin.setRoleCode("ADMIN");
        when(adminGuard.requireAdmin(1L)).thenReturn(admin);

        DataMigrationTask task = new DataMigrationTask();
        task.setId(1001L);
        task.setTaskType(DataMigrationConstants.TASK_TYPE_EXPORT);
        task.setStatus(DataMigrationConstants.TASK_STATUS_SUCCESS);
        task.setFileUrl("/tmp/data-migrations/demo.zip");
        when(taskMapper.selectById(1001L)).thenReturn(task);
        when(storageService.resolve("/tmp/data-migrations/demo.zip")).thenReturn(java.nio.file.Path.of("/tmp/data-migrations/demo.zip"));

        DataMigrationService service = new DataMigrationService(
                taskMapper,
                taskItemMapper,
                systemAppMapper,
                adminGuard,
                storageService,
                archiveService,
                taskExecutionService,
                new ObjectMapper().findAndRegisterModules(),
                Validation.buildDefaultValidatorFactory().getValidator(),
                new NoopTaskExecutor(),
                List.of(new StubHandler()),
                "/api/system/data-migrations/tasks/"
        );

        service.deleteExportTask(1L, 1001L);

        verify(storageService).resolve("/tmp/data-migrations/demo.zip");
        verify(storageService).deleteQuietly(java.nio.file.Path.of("/tmp/data-migrations/demo.zip"));
        verify(taskItemMapper).delete(any());
        verify(taskMapper).deleteById(1001L);
    }

    private static class NoopTaskExecutor implements TaskExecutor {

        @Override
        public void execute(Runnable task) {
        }
    }

    private static class StubHandler implements MigrationResourceHandler {

        @Override
        public String resourceCode() {
            return DataMigrationConstants.SYSTEM_RESOURCE_USERS;
        }

        @Override
        public String resourceName() {
            return "用户与账号";
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
            return "system/users.json";
        }

        @Override
        public int order() {
            return 10;
        }

        @Override
        public MigrationResourceExportData exportData(ExportContext context) {
            throw new UnsupportedOperationException();
        }

        @Override
        public MigrationResourceImportResult importData(ImportContext context) {
            throw new UnsupportedOperationException();
        }
    }
}
