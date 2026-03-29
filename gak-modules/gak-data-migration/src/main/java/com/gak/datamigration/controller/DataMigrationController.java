package com.gak.datamigration.controller;

import com.gak.datamigration.dto.CreateDataMigrationExportRequest;
import com.gak.datamigration.dto.DataMigrationTaskQueryRequest;
import com.gak.datamigration.service.DataMigrationService;
import com.gak.datamigration.vo.DataMigrationResourcesVO;
import com.gak.datamigration.vo.DataMigrationTaskVO;
import com.gak.datamigration.vo.DeleteDataMigrationTaskVO;
import com.gak.framework.response.ApiResponse;
import com.gak.framework.response.PagedResult;
import com.gak.user.service.user.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 数据迁移控制器。
 */
@RestController
@RequestMapping("/system/data-migrations")
public class DataMigrationController {

    private final DataMigrationService dataMigrationService;
    private final TokenService tokenService;

    public DataMigrationController(DataMigrationService dataMigrationService,
                                   TokenService tokenService) {
        this.dataMigrationService = dataMigrationService;
        this.tokenService = tokenService;
    }

    @GetMapping("/resources")
    public ApiResponse<DataMigrationResourcesVO> resources(HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(dataMigrationService.resources(currentUserId));
    }

    @GetMapping("/tasks")
    public ApiResponse<PagedResult<DataMigrationTaskVO>> page(@Valid DataMigrationTaskQueryRequest request,
                                                              HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(dataMigrationService.page(currentUserId, request));
    }

    @GetMapping("/tasks/{taskId}")
    public ApiResponse<DataMigrationTaskVO> detail(@PathVariable Long taskId,
                                                   HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(dataMigrationService.detail(currentUserId, taskId));
    }

    @PostMapping("/exports")
    public ApiResponse<DataMigrationTaskVO> createExport(@Valid @RequestBody CreateDataMigrationExportRequest request,
                                                         HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(dataMigrationService.createExportTask(currentUserId, request));
    }

    @GetMapping("/tasks/{taskId}/download")
    public ResponseEntity<Resource> download(@PathVariable Long taskId,
                                             HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        DataMigrationService.DownloadFile downloadFile = dataMigrationService.download(currentUserId, taskId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment().filename(downloadFile.fileName()).build());
        if (downloadFile.fileSize() != null) {
            headers.setContentLength(downloadFile.fileSize());
        }
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(downloadFile.mediaType())
                .body(downloadFile.resource());
    }

    @DeleteMapping("/tasks/{taskId}")
    public ApiResponse<DeleteDataMigrationTaskVO> deleteTask(@PathVariable Long taskId,
                                                             HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(dataMigrationService.deleteExportTask(currentUserId, taskId));
    }

    @PostMapping("/imports")
    public ApiResponse<DataMigrationTaskVO> createImport(@RequestPart("file") MultipartFile file,
                                                         @RequestPart("metadata") String metadata,
                                                         HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(dataMigrationService.createImportTask(currentUserId, file, metadata));
    }
}
