package com.gak.worklog.controller;

import com.gak.framework.response.ApiResponse;
import com.gak.user.service.user.TokenService;
import com.gak.worklog.dto.CreateWorkLogRequest;
import com.gak.worklog.dto.UpdateWorkLogRequest;
import com.gak.worklog.dto.WeeklyWorkLogBriefResponse;
import com.gak.worklog.dto.WorkLogResponse;
import com.gak.worklog.service.WorkLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工作日志控制器。
 */
@RestController
@RequestMapping("/work-logs")
public class WorkLogController {

    private final WorkLogService workLogService;
    private final TokenService tokenService;

    public WorkLogController(WorkLogService workLogService, TokenService tokenService) {
        this.workLogService = workLogService;
        this.tokenService = tokenService;
    }

    /**
     * 新增工作日志。
     *
     * @param request 请求参数
     * @return 工作日志详情
     */
    @PostMapping
    public ApiResponse<WorkLogResponse> create(@Valid @RequestBody CreateWorkLogRequest request,
                                               HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(workLogService.create(currentUserId, request));
    }

    /**
     * 删除工作日志。
     *
     * @param id 主键 ID
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        workLogService.delete(currentUserId, id);
        return ApiResponse.success();
    }

    /**
     * 更新工作日志。
     *
     * @param id 主键 ID
     * @param request 请求参数
     * @return 更新后详情
     */
    @PutMapping("/{id}")
    public ApiResponse<WorkLogResponse> update(@PathVariable Long id,
                                               @Valid @RequestBody UpdateWorkLogRequest request,
                                               HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(workLogService.update(currentUserId, id, request));
    }

    /**
     * 查询工作日志详情。
     *
     * @param id 主键 ID
     * @return 工作日志详情
     */
    @GetMapping("/{id}")
    public ApiResponse<WorkLogResponse> get(@PathVariable Long id, HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(workLogService.get(currentUserId, id));
    }

    /**
     * 条件查询工作日志列表。
     *
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @param typeCode 类型编码
     * @return 工作日志列表
     */
    @GetMapping
    public ApiResponse<List<WorkLogResponse>> list(
            @RequestParam(value = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(value = "typeCode", required = false) String typeCode,
            HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(workLogService.list(currentUserId, startDate, endDate, typeCode));
    }

    /**
     * 查询最近一周日志简述。
     *
     * @param refDate 参考日期
     * @return 最近一周日志简述
     */
    @GetMapping("/weekly-brief")
    public ApiResponse<List<WeeklyWorkLogBriefResponse>> listWeeklyBrief(
            @RequestParam(value = "refDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate refDate,
            HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(workLogService.listWeeklyBrief(currentUserId, refDate));
    }
}
