package com.gak.healthrecord.controller;

import com.gak.framework.security.RequireAppPermission;
import com.gak.framework.response.ApiResponse;
import com.gak.framework.response.PagedResult;
import com.gak.healthrecord.dto.HealthRecordQueryRequest;
import com.gak.healthrecord.dto.HealthReportQueryRequest;
import com.gak.healthrecord.dto.HealthTrendQueryRequest;
import com.gak.healthrecord.dto.HealthVisitQueryRequest;
import com.gak.healthrecord.dto.SaveHealthRecordRequest;
import com.gak.healthrecord.dto.SaveHealthReportDetailsRequest;
import com.gak.healthrecord.dto.SaveHealthReportRequest;
import com.gak.healthrecord.dto.SaveHealthVisitRequest;
import com.gak.healthrecord.service.HealthRecordService;
import com.gak.healthrecord.service.HealthReportDetailService;
import com.gak.healthrecord.vo.HealthFileUploadVO;
import com.gak.healthrecord.vo.HealthRecordVO;
import com.gak.healthrecord.vo.HealthReportDetailsVO;
import com.gak.healthrecord.vo.HealthReportFileResource;
import com.gak.healthrecord.vo.HealthReportVO;
import com.gak.healthrecord.vo.HealthSummaryVO;
import com.gak.healthrecord.vo.HealthTrendVO;
import com.gak.healthrecord.vo.HealthVisitVO;
import com.gak.user.service.user.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 健康应用控制器。
 */
@RestController
@RequireAppPermission("APP_HEALTH_RECORD")
@RequestMapping("/health-records")
public class HealthRecordController {

    private final HealthRecordService healthRecordService;
    private final HealthReportDetailService healthReportDetailService;
    private final TokenService tokenService;

    public HealthRecordController(HealthRecordService healthRecordService,
                                  HealthReportDetailService healthReportDetailService,
                                  TokenService tokenService) {
        this.healthRecordService = healthRecordService;
        this.healthReportDetailService = healthReportDetailService;
        this.tokenService = tokenService;
    }

    @GetMapping
    public ApiResponse<PagedResult<HealthRecordVO>> page(@Valid HealthRecordQueryRequest request,
                                                         HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(healthRecordService.pageRecords(currentUserId, request));
    }

    @GetMapping("/{recordId}")
    public ApiResponse<HealthRecordVO> getRecord(@PathVariable Long recordId,
                                                 HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(healthRecordService.getRecord(currentUserId, recordId));
    }

    @PostMapping
    public ApiResponse<HealthRecordVO> create(@Valid @RequestBody SaveHealthRecordRequest request,
                                              HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(healthRecordService.createRecord(currentUserId, request));
    }

    @PutMapping("/{id}")
    public ApiResponse<HealthRecordVO> update(@PathVariable Long id,
                                              @Valid @RequestBody SaveHealthRecordRequest request,
                                              HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(healthRecordService.updateRecord(currentUserId, id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        healthRecordService.deleteRecord(currentUserId, id);
        return ApiResponse.success();
    }

    @GetMapping("/summary")
    public ApiResponse<HealthSummaryVO> summary(HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(healthRecordService.getSummary(currentUserId));
    }

    @GetMapping("/trends")
    public ApiResponse<HealthTrendVO> trends(@Valid HealthTrendQueryRequest request,
                                             HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(healthRecordService.getTrends(currentUserId, request));
    }

    @GetMapping("/reports")
    public ApiResponse<PagedResult<HealthReportVO>> listReports(@Valid HealthReportQueryRequest request,
                                                                HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(healthRecordService.pageReports(currentUserId, request));
    }

    @PostMapping("/reports")
    public ApiResponse<HealthReportVO> createReport(@Valid @RequestBody SaveHealthReportRequest request,
                                                    HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(healthRecordService.createReport(currentUserId, request));
    }

    @PutMapping("/reports/{id}")
    public ApiResponse<HealthReportVO> updateReport(@PathVariable Long id,
                                                    @Valid @RequestBody SaveHealthReportRequest request,
                                                    HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(healthRecordService.updateReport(currentUserId, id, request));
    }

    @DeleteMapping("/reports/{id}")
    public ApiResponse<Void> deleteReport(@PathVariable Long id, HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        healthRecordService.deleteReport(currentUserId, id);
        return ApiResponse.success();
    }

    @GetMapping("/reports/{reportId}/details")
    public ApiResponse<HealthReportDetailsVO> getReportDetails(@PathVariable Long reportId,
                                                               HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(healthReportDetailService.getDetails(currentUserId, reportId));
    }

    @PutMapping("/reports/{reportId}/details")
    public ApiResponse<HealthReportDetailsVO> saveReportDetails(@PathVariable Long reportId,
                                                                @Valid @RequestBody SaveHealthReportDetailsRequest request,
                                                                HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(healthReportDetailService.saveDetails(currentUserId, reportId, request));
    }

    @PostMapping("/reports/upload")
    public ApiResponse<HealthFileUploadVO> uploadReportFile(@RequestParam("file") MultipartFile file,
                                                            HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(healthRecordService.uploadReportFile(currentUserId, file));
    }

    @GetMapping("/report-files/{fileName:.+}")
    public ResponseEntity<Resource> getReportFile(@PathVariable String fileName,
                                                  HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        HealthReportFileResource reportFileResource = healthRecordService.loadReportFile(currentUserId, fileName);
        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(reportFileResource.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(reportFileResource.mediaType())
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(reportFileResource.resource());
    }

    @GetMapping("/visits")
    public ApiResponse<PagedResult<HealthVisitVO>> listVisits(@Valid HealthVisitQueryRequest request,
                                                              HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(healthRecordService.pageVisits(currentUserId, request));
    }

    @GetMapping("/visits/{id}")
    public ApiResponse<HealthVisitVO> getVisit(@PathVariable Long id, HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(healthRecordService.getVisit(currentUserId, id));
    }

    @PostMapping("/visits")
    public ApiResponse<HealthVisitVO> createVisit(@Valid @RequestBody SaveHealthVisitRequest request,
                                                  HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(healthRecordService.createVisit(currentUserId, request));
    }

    @PutMapping("/visits/{id}")
    public ApiResponse<HealthVisitVO> updateVisit(@PathVariable Long id,
                                                  @Valid @RequestBody SaveHealthVisitRequest request,
                                                  HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(healthRecordService.updateVisit(currentUserId, id, request));
    }

    @DeleteMapping("/visits/{id}")
    public ApiResponse<Void> deleteVisit(@PathVariable Long id, HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        healthRecordService.deleteVisit(currentUserId, id);
        return ApiResponse.success();
    }
}
